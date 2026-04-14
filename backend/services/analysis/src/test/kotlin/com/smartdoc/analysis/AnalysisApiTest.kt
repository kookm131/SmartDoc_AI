package com.smartdoc.analysis

import org.hamcrest.Matchers.blankOrNullString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var analysisJobRepository: AnalysisJobRepository

    @MockBean
    private lateinit var documentLookupPort: DocumentLookupPort

    @MockBean
    private lateinit var aiAnalysisPort: AiAnalysisPort

    @BeforeEach
    fun setUp() {
        analysisJobRepository.deleteAll()
        reset(documentLookupPort, aiAnalysisPort)
    }

    @Test
    fun `creates analysis job and advances local state while syncing document status`() {
        val documentId = "11111111-1111-1111-1111-111111111111"
        `when`(documentLookupPort.exists(documentId)).thenReturn(true)
        `when`(aiAnalysisPort.submit(AiAnalysisCommand(documentId))).thenReturn(
            AiAnalysisResult(state = "QUEUED", provider = "local-stub")
        )

        val created = mockMvc.perform(
            post("/api/v1/analysis/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"$documentId"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.documentId").value(documentId))
            .andExpect(jsonPath("$.state").value("QUEUED"))
            .andReturn()

        val jobId = Regex(""""jobId":"([^"]+)"""")
            .find(created.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("jobId not found")

        verify(documentLookupPort).updateStatus(documentId, "ANALYSIS_QUEUED")

        Thread.sleep(2100)
        mockMvc.perform(get("/api/v1/analysis/jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("PROCESSING"))

        verify(documentLookupPort, atLeastOnce()).updateStatus(documentId, "ANALYSIS_PROCESSING")

        Thread.sleep(2200)
        mockMvc.perform(get("/api/v1/analysis/jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("COMPLETED"))

        verify(documentLookupPort, atLeastOnce()).updateStatus(documentId, "ANALYSIS_COMPLETED")
    }

    @Test
    fun `returns standard not found error for missing document`() {
        val documentId = "22222222-2222-2222-2222-222222222222"
        `when`(documentLookupPort.exists(documentId)).thenReturn(false)

        mockMvc.perform(
            post("/api/v1/analysis/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"$documentId"}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
    }
}
