package com.smartdoc.document

import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.blankOrNullString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class DocumentApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var documentRepository: DocumentRepository

    @BeforeEach
    fun setUp() {
        documentRepository.deleteAll()
    }

    @Test
    fun `creates document and updates analysis status`() {
        val created = mockMvc.perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "filename": "contract.pdf",
                      "fileKey": "uploads/contract.pdf",
                      "contentType": "application/pdf"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andReturn()

        val documentId = Regex(""""documentId":"([^"]+)"""")
            .find(created.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("documentId not found")

        mockMvc.perform(
            patch("/api/v1/documents/$documentId/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"ANALYSIS_COMPLETED"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.documentId").value(documentId))
            .andExpect(jsonPath("$.status").value("ANALYSIS_COMPLETED"))
    }

    @Test
    fun `returns standard validation error`() {
        mockMvc.perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"","fileKey":"uploads/contract.pdf"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
    }
}
