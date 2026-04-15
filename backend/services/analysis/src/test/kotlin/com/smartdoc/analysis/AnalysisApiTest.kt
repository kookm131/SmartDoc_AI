package com.smartdoc.analysis

import org.hamcrest.Matchers.blankOrNullString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
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
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var analysisJobRepository: AnalysisJobRepository

    @Autowired
    private lateinit var keywordDetectionRepository: KeywordDetectionRepository

    @MockBean
    private lateinit var documentLookupPort: DocumentLookupPort

    @MockBean
    private lateinit var aiAnalysisPort: AiAnalysisPort

    @MockBean
    private lateinit var notificationDispatchPort: NotificationDispatchPort

    @BeforeEach
    fun setUp() {
        keywordDetectionRepository.deleteAll()
        analysisJobRepository.deleteAll()
        reset(documentLookupPort, aiAnalysisPort, notificationDispatchPort)
    }

    @Test
    fun `creates analysis job and advances local state while syncing document status`() {
        val documentId = "11111111-1111-1111-1111-111111111111"
        `when`(documentLookupPort.exists(documentId, "local-dev-user")).thenReturn(true)
        `when`(documentLookupPort.get(documentId, "local-dev-user")).thenReturn(
            DocumentInfo(
                documentId = documentId,
                filename = "contract-review.txt",
                fileKey = "uploads/contract-review.txt",
                contentType = "text/plain"
            )
        )
        `when`(documentLookupPort.readTextContent(documentId, "local-dev-user")).thenReturn("긴급 계약 검토 알림이 필요한 문서입니다.")
        `when`(aiAnalysisPort.submit(AiAnalysisCommand(documentId))).thenReturn(
            AiAnalysisResult(state = "QUEUED", provider = "local-stub")
        )
        `when`(
            notificationDispatchPort.dispatchAnalysisCompleted(
                documentId,
                "local-dev-user",
                listOf("계약", "검토", "알림", "긴급"),
                98
            )
        ).thenReturn(true)

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

        verify(documentLookupPort).updateStatus(documentId, "ANALYSIS_QUEUED", "local-dev-user")

        analysisJobRepository.findById(jobId).get().also {
            it.createdAt = Instant.now().minusSeconds(2)
            analysisJobRepository.save(it)
        }

        mockMvc.perform(get("/api/v1/analysis/jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("PROCESSING"))

        verify(documentLookupPort, atLeastOnce()).updateStatus(documentId, "ANALYSIS_PROCESSING", "local-dev-user")

        analysisJobRepository.findById(jobId).get().also {
            it.createdAt = Instant.now().minusSeconds(4)
            analysisJobRepository.save(it)
        }

        mockMvc.perform(get("/api/v1/analysis/jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("COMPLETED"))
            .andExpect(jsonPath("$.resultSummary").value("로컬 분석이 완료되었습니다. 업로드된 텍스트 파일 내용 기준으로 계약, 검토, 알림, 긴급 키워드를 감지했습니다."))
            .andExpect(jsonPath("$.riskScore").value(98))
            .andExpect(jsonPath("$.keywords[0]").value("계약"))

        check(keywordDetectionRepository.findByAnalysisJobId(jobId).size == 4) {
            "expected 4 keyword detections"
        }

        mockMvc.perform(get("/api/v1/analysis/jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keywords[0]").value("계약"))

        check(keywordDetectionRepository.findByAnalysisJobId(jobId).size == 4) {
            "keyword detections should not be duplicated"
        }

        verify(documentLookupPort, atLeastOnce()).updateStatus(documentId, "ANALYSIS_COMPLETED", "local-dev-user")
        verify(notificationDispatchPort).dispatchAnalysisCompleted(
            documentId,
            "local-dev-user",
            listOf("계약", "검토", "알림", "긴급"),
            98
        )
    }

    @Test
    fun `marks local analysis failure and retries failed job`() {
        val documentId = "44444444-4444-4444-4444-444444444444"
        `when`(documentLookupPort.exists(documentId, "local-dev-user")).thenReturn(true)
        `when`(documentLookupPort.get(documentId, "local-dev-user")).thenReturn(
            DocumentInfo(
                documentId = documentId,
                filename = "analysis-fail.txt",
                fileKey = "uploads/analysis-fail.txt",
                contentType = "text/plain"
            ),
            DocumentInfo(
                documentId = documentId,
                filename = "contract-review.txt",
                fileKey = "uploads/contract-review.txt",
                contentType = "text/plain"
            )
        )
        `when`(documentLookupPort.readTextContent(documentId, "local-dev-user")).thenReturn(
            "분석실패",
            "긴급 계약 검토 문서입니다."
        )
        `when`(aiAnalysisPort.submit(AiAnalysisCommand(documentId))).thenReturn(
            AiAnalysisResult(state = "QUEUED", provider = "local-stub")
        )
        `when`(
            notificationDispatchPort.dispatchAnalysisCompleted(
                documentId,
                "local-dev-user",
                listOf("계약", "검토", "긴급"),
                86
            )
        ).thenReturn(true)

        val created = mockMvc.perform(
            post("/api/v1/analysis/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"$documentId"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.state").value("QUEUED"))
            .andReturn()

        val jobId = Regex(""""jobId":"([^"]+)"""")
            .find(created.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("jobId not found")

        analysisJobRepository.findById(jobId).get().also {
            it.createdAt = Instant.now().minusSeconds(4)
            analysisJobRepository.save(it)
        }

        mockMvc.perform(get("/api/v1/analysis/jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("FAILED"))
            .andExpect(jsonPath("$.errorCode").value("LOCAL_ANALYSIS_FAILED"))
            .andExpect(jsonPath("$.errorMessage").value("로컬 분석 실패 마커가 감지되었습니다."))
            .andExpect(jsonPath("$.failedAt").value(not(blankOrNullString())))

        check(keywordDetectionRepository.findByAnalysisJobId(jobId).isEmpty()) {
            "failed analysis should not save keyword detections"
        }
        verify(documentLookupPort, atLeastOnce()).updateStatus(documentId, "ANALYSIS_FAILED", "local-dev-user")

        mockMvc.perform(post("/api/v1/analysis/jobs/$jobId/retry"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("QUEUED"))
            .andExpect(jsonPath("$.errorCode").value(nullValue()))
            .andExpect(jsonPath("$.failedAt").value(nullValue()))

        analysisJobRepository.findById(jobId).get().also {
            it.createdAt = Instant.now().minusSeconds(4)
            analysisJobRepository.save(it)
        }

        mockMvc.perform(get("/api/v1/analysis/jobs/$jobId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("COMPLETED"))
            .andExpect(jsonPath("$.riskScore").value(86))
            .andExpect(jsonPath("$.keywords[0]").value("계약"))

        check(keywordDetectionRepository.findByAnalysisJobId(jobId).size == 3) {
            "retried analysis should save successful keyword detections"
        }
        verify(documentLookupPort, atLeastOnce()).updateStatus(documentId, "ANALYSIS_QUEUED", "local-dev-user")
        verify(documentLookupPort, atLeastOnce()).updateStatus(documentId, "ANALYSIS_COMPLETED", "local-dev-user")
    }

    @Test
    fun `returns standard not found error for missing document`() {
        val documentId = "22222222-2222-2222-2222-222222222222"
        `when`(documentLookupPort.exists(documentId, "local-dev-user")).thenReturn(false)

        mockMvc.perform(
            post("/api/v1/analysis/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"$documentId"}""")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
    }

    @Test
    fun `separates analysis jobs by owner header`() {
        val documentId = "33333333-3333-3333-3333-333333333333"
        `when`(documentLookupPort.exists(documentId, "alice-user")).thenReturn(true)
        `when`(aiAnalysisPort.submit(AiAnalysisCommand(documentId))).thenReturn(
            AiAnalysisResult(state = "QUEUED", provider = "local-stub")
        )

        val created = mockMvc.perform(
            post("/api/v1/analysis/jobs")
                .header("X-SmartDoc-User-Id", "alice-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"$documentId"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerUserId").value("alice-user"))
            .andReturn()

        val jobId = Regex(""""jobId":"([^"]+)"""")
            .find(created.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("jobId not found")

        mockMvc.perform(
            get("/api/v1/analysis/jobs/$jobId")
                .header("X-SmartDoc-User-Id", "bob-user")
        )
            .andExpect(status().isNotFound)
    }
}
