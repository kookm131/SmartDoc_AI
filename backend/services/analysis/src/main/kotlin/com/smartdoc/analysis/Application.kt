package com.smartdoc.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@RestController
@RequestMapping("/api/v1")
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf(
        "service" to "analysis",
        "status" to "ok"
    )
}

data class AnalysisJobCreateRequest(
    val documentId: String
)

data class AnalysisJobResponse(
    val jobId: String,
    val ownerUserId: String,
    val documentId: String,
    val state: String,
    val createdAt: Instant,
    val analysisProvider: String,
    val resultSummary: String?,
    val riskScore: Int?,
    val keywords: List<String>,
    val errorCode: String?,
    val errorMessage: String?,
    val failedAt: Instant?
)

data class AiAnalysisCommand(
    val documentId: String
)

data class AiAnalysisResult(
    val state: String,
    val provider: String
)

interface AiAnalysisPort {
    fun submit(command: AiAnalysisCommand): AiAnalysisResult
}

interface DocumentLookupPort {
    fun exists(documentId: String, ownerUserId: String): Boolean
    fun get(documentId: String, ownerUserId: String): DocumentInfo
    fun readTextContent(documentId: String, ownerUserId: String): String?
    fun updateStatus(documentId: String, status: String, ownerUserId: String)
}

data class DocumentInfo(
    val documentId: String,
    val filename: String,
    val fileKey: String,
    val contentType: String?
)

data class DocumentContentInfo(
    val documentId: String,
    val fileKey: String,
    val contentType: String?,
    val textContent: String?
)

interface NotificationDispatchPort {
    fun dispatchAnalysisCompleted(
        documentId: String,
        ownerUserId: String,
        keywords: List<String>,
        riskScore: Int?
    ): Boolean
}

@Service
@Profile("local | mariadb")
class LocalAiAnalysisAdapter : AiAnalysisPort {
    override fun submit(command: AiAnalysisCommand): AiAnalysisResult =
        AiAnalysisResult(
            state = "QUEUED",
            provider = "local-stub"
        )
}

@Service
@Profile("aws")
class AwsAiAnalysisAdapter : AiAnalysisPort {
    override fun submit(command: AiAnalysisCommand): AiAnalysisResult =
        AiAnalysisResult(
            state = "QUEUED",
            provider = "aws-textract-comprehend"
        )
}

@Service
@Profile("local | mariadb")
class LocalDocumentLookupAdapter(
    @Value("\${smartdoc.document.base-url:http://localhost:8081}")
    private val documentBaseUrl: String,
    @Value("\${smartdoc.document.connect-timeout-ms:1000}")
    private val connectTimeoutMs: Int,
    @Value("\${smartdoc.document.read-timeout-ms:2000}")
    private val readTimeoutMs: Int
) : DocumentLookupPort {
    private val client = RestClient.builder()
        .baseUrl(documentBaseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMs)
                setReadTimeout(readTimeoutMs)
            }
        )
        .build()

    override fun exists(documentId: String, ownerUserId: String): Boolean = try {
        client.get()
            .uri("/api/v1/documents/{id}", documentId)
            .header(SMARTDOC_USER_ID_HEADER, ownerUserId)
            .retrieve()
            .toBodilessEntity()
        true
    } catch (ex: HttpClientErrorException.NotFound) {
        false
    } catch (ex: HttpClientErrorException) {
        throw ExternalServiceException("document service rejected lookup with status ${ex.statusCode.value()}")
    } catch (ex: RestClientException) {
        throw ExternalServiceException("document service lookup failed")
    }

    override fun get(documentId: String, ownerUserId: String): DocumentInfo = try {
        client.get()
            .uri("/api/v1/documents/{id}", documentId)
            .header(SMARTDOC_USER_ID_HEADER, ownerUserId)
            .retrieve()
            .body(DocumentInfo::class.java)
            ?: throw ExternalServiceException("document service returned empty lookup response")
    } catch (ex: HttpClientErrorException.NotFound) {
        throw ResourceNotFoundException("document not found: $documentId")
    } catch (ex: HttpClientErrorException) {
        throw ExternalServiceException("document service rejected lookup with status ${ex.statusCode.value()}")
    } catch (ex: RestClientException) {
        throw ExternalServiceException("document service lookup failed")
    }

    override fun readTextContent(documentId: String, ownerUserId: String): String? = try {
        client.get()
            .uri("/api/v1/documents/{id}/content", documentId)
            .header(SMARTDOC_USER_ID_HEADER, ownerUserId)
            .retrieve()
            .body(DocumentContentInfo::class.java)
            ?.textContent
    } catch (ex: HttpClientErrorException.NotFound) {
        throw ResourceNotFoundException("document not found: $documentId")
    } catch (ex: RestClientException) {
        null
    }

    override fun updateStatus(documentId: String, status: String, ownerUserId: String) {
        try {
            client.post()
                .uri("/api/v1/documents/{id}/status", documentId)
                .header(SMARTDOC_USER_ID_HEADER, ownerUserId)
                .body(mapOf("status" to status))
                .retrieve()
                .toBodilessEntity()
        } catch (ex: HttpClientErrorException.NotFound) {
            throw ResourceNotFoundException("document not found: $documentId")
        } catch (ex: HttpClientErrorException) {
            throw ExternalServiceException("document service rejected status update with status ${ex.statusCode.value()}")
        } catch (ex: RestClientException) {
            throw ExternalServiceException("document status update failed")
        }
    }
}

@Service
@Profile("aws")
class AwsDocumentLookupAdapter : DocumentLookupPort {
    override fun exists(documentId: String, ownerUserId: String): Boolean = true
    override fun get(documentId: String, ownerUserId: String): DocumentInfo =
        DocumentInfo(
            documentId = documentId,
            filename = "aws-placeholder.pdf",
            fileKey = "uploads/aws-placeholder.pdf",
            contentType = "application/pdf"
        )

    override fun readTextContent(documentId: String, ownerUserId: String): String? = null
    override fun updateStatus(documentId: String, status: String, ownerUserId: String) = Unit
}

@Service
@Profile("local | mariadb")
class LocalNotificationDispatchAdapter(
    @Value("\${smartdoc.notification.base-url:http://localhost:8083}")
    private val notificationBaseUrl: String,
    @Value("\${smartdoc.notification.connect-timeout-ms:1000}")
    private val connectTimeoutMs: Int,
    @Value("\${smartdoc.notification.read-timeout-ms:2000}")
    private val readTimeoutMs: Int
) : NotificationDispatchPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val client = RestClient.builder()
        .baseUrl(notificationBaseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(connectTimeoutMs)
                setReadTimeout(readTimeoutMs)
            }
        )
        .build()

    override fun dispatchAnalysisCompleted(
        documentId: String,
        ownerUserId: String,
        keywords: List<String>,
        riskScore: Int?
    ): Boolean {
        val keywordText = keywords.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "없음"
        val riskScoreText = riskScore?.toString() ?: "미정"
        val message = "분석 완료: 위험 점수 ${riskScoreText}점, 키워드 $keywordText"

        return try {
            client.post()
                .uri("/api/v1/notifications/dispatch")
                .header(SMARTDOC_USER_ID_HEADER, ownerUserId)
                .body(
                    mapOf(
                        "documentId" to documentId,
                        "keywords" to keywords,
                        "riskScore" to riskScore,
                        "message" to message
                    )
                )
                .retrieve()
                .toBodilessEntity()
            true
        } catch (ex: RestClientException) {
            logger.warn("analysis completion notification dispatch failed: {}", ex.message)
            false
        }
    }
}

@Service
@Profile("aws")
class AwsNotificationDispatchAdapter : NotificationDispatchPort {
    override fun dispatchAnalysisCompleted(
        documentId: String,
        ownerUserId: String,
        keywords: List<String>,
        riskScore: Int?
    ): Boolean = true
}

data class ApiErrorResponse(
    val timestamp: Instant,
    val path: String,
    val code: String,
    val message: String,
    val traceId: String
)

class ResourceNotFoundException(message: String) : RuntimeException(message)
class ExternalServiceException(message: String) : RuntimeException(message)
class LocalAnalysisFailedException(message: String) : RuntimeException(message)

private const val SMARTDOC_USER_ID_HEADER = "X-SmartDoc-User-Id"
private const val LOCAL_DEV_OWNER_USER_ID = "local-dev-user"

private fun ownerUserIdFrom(request: HttpServletRequest): String =
    request.getHeader(SMARTDOC_USER_ID_HEADER)?.trim()?.takeIf { it.isNotBlank() } ?: LOCAL_DEV_OWNER_USER_ID

@Entity
@Table(name = "analysis_jobs")
class AnalysisJobEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "document_id", nullable = false, length = 36)
    var documentId: String = "",

    @Column(name = "owner_user_id", nullable = false, length = 36)
    var ownerUserId: String = LOCAL_DEV_OWNER_USER_ID,

    @Column(nullable = false, length = 32)
    var state: String = "QUEUED",

    @Column(name = "analysis_provider", nullable = false, length = 64)
    var analysisProvider: String = "local-stub",

    @Column(name = "result_summary", length = 1024)
    var resultSummary: String? = null,

    @Column(name = "risk_score")
    var riskScore: Int? = null,

    @Column(length = 512)
    var keywords: String = "",

    @Column(name = "error_code", length = 64)
    var errorCode: String? = null,

    @Column(name = "error_message", length = 1024)
    var errorMessage: String? = null,

    @Column(name = "failed_at")
    var failedAt: Instant? = null,

    @Column(name = "notification_dispatched_at")
    var notificationDispatchedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
    }
}

interface AnalysisJobRepository : JpaRepository<AnalysisJobEntity, String> {
    fun findByIdAndOwnerUserId(id: String, ownerUserId: String): AnalysisJobEntity?
}

@Entity
@Table(
    name = "keyword_detections",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_keyword_detections_job_keyword",
            columnNames = ["analysis_job_id", "keyword"]
        )
    ]
)
class KeywordDetectionEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "analysis_job_id", nullable = false, length = 36)
    var analysisJobId: String = "",

    @Column(nullable = false, length = 128)
    var keyword: String = "",

    @Column(nullable = false)
    var confidence: Double = 1.0,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
    }
}

interface KeywordDetectionRepository : JpaRepository<KeywordDetectionEntity, String> {
    fun findByAnalysisJobId(analysisJobId: String): List<KeywordDetectionEntity>
    fun existsByAnalysisJobIdAndKeyword(analysisJobId: String, keyword: String): Boolean
}

@Service
class AnalysisJobService(
    private val aiAnalysisPort: AiAnalysisPort,
    private val documentLookupPort: DocumentLookupPort,
    private val notificationDispatchPort: NotificationDispatchPort,
    private val analysisJobRepository: AnalysisJobRepository,
    private val keywordDetectionRepository: KeywordDetectionRepository
) {
    fun create(request: AnalysisJobCreateRequest, ownerUserId: String): AnalysisJobResponse {
        val docId = request.documentId.trim()
        if (!documentLookupPort.exists(docId, ownerUserId)) {
            throw ResourceNotFoundException("document not found: $docId")
        }

        val result = aiAnalysisPort.submit(AiAnalysisCommand(documentId = docId))

        val saved = analysisJobRepository.save(
            AnalysisJobEntity(
                documentId = docId,
                ownerUserId = ownerUserId,
                state = result.state,
                analysisProvider = result.provider
            )
        )
        documentLookupPort.updateStatus(docId, toDocumentStatus(saved.state), ownerUserId)
        return toResponse(saved)
    }

    fun get(jobId: String, ownerUserId: String): AnalysisJobResponse =
        analysisJobRepository.findByIdAndOwnerUserId(jobId, ownerUserId)
            ?.let(::advanceLocalState)
            ?.let(::toResponse)
            ?: throw ResourceNotFoundException("analysis job not found: $jobId")

    fun retry(jobId: String, ownerUserId: String): AnalysisJobResponse {
        val found = analysisJobRepository.findByIdAndOwnerUserId(jobId, ownerUserId)
            ?: throw ResourceNotFoundException("analysis job not found: $jobId")

        require(found.state == "FAILED") { "only FAILED analysis jobs can be retried" }

        keywordDetectionRepository.deleteAll(keywordDetectionRepository.findByAnalysisJobId(found.id))
        found.state = "QUEUED"
        found.createdAt = Instant.now()
        found.resultSummary = null
        found.riskScore = null
        found.keywords = ""
        found.errorCode = null
        found.errorMessage = null
        found.failedAt = null
        found.notificationDispatchedAt = null

        val saved = analysisJobRepository.save(found)
        documentLookupPort.updateStatus(saved.documentId, toDocumentStatus(saved.state), saved.ownerUserId)
        return toResponse(saved)
    }

    private fun advanceLocalState(entity: AnalysisJobEntity): AnalysisJobEntity {
        if (entity.state == "COMPLETED") {
            return dispatchCompletionNotification(entity)
        }
        if (entity.state == "FAILED") {
            return entity
        }

        val ageSeconds = Duration.between(entity.createdAt, Instant.now()).seconds
        val nextState = when {
            ageSeconds >= 4 -> "COMPLETED"
            ageSeconds >= 2 -> "PROCESSING"
            else -> entity.state
        }

        if (nextState == entity.state) {
            return entity
        }

        entity.state = nextState
        if (nextState == "COMPLETED") {
            try {
                val result = analyzeDocument(entity.documentId, entity.ownerUserId)
                entity.resultSummary = result.summary
                entity.riskScore = result.riskScore
                entity.keywords = result.keywords.joinToString(",")
                entity.errorCode = null
                entity.errorMessage = null
                entity.failedAt = null
            } catch (ex: LocalAnalysisFailedException) {
                entity.state = "FAILED"
                entity.resultSummary = null
                entity.riskScore = null
                entity.keywords = ""
                entity.errorCode = "LOCAL_ANALYSIS_FAILED"
                entity.errorMessage = ex.message ?: "local analysis failed"
                entity.failedAt = Instant.now()
                entity.notificationDispatchedAt = null
            }
        }
        val saved = analysisJobRepository.save(entity)
        if (saved.state == "COMPLETED") {
            saveKeywordDetections(saved)
        }
        documentLookupPort.updateStatus(saved.documentId, toDocumentStatus(saved.state), saved.ownerUserId)
        return dispatchCompletionNotification(saved)
    }

    private fun dispatchCompletionNotification(entity: AnalysisJobEntity): AnalysisJobEntity {
        if (entity.state != "COMPLETED" || entity.notificationDispatchedAt != null) {
            return entity
        }

        val dispatched = notificationDispatchPort.dispatchAnalysisCompleted(
            documentId = entity.documentId,
            ownerUserId = entity.ownerUserId,
            keywords = keywordsFor(entity),
            riskScore = entity.riskScore
        )

        if (!dispatched) {
            return entity
        }

        entity.notificationDispatchedAt = Instant.now()
        return analysisJobRepository.save(entity)
    }

    private fun saveKeywordDetections(entity: AnalysisJobEntity) {
        entity.keywords.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { keyword ->
                if (!keywordDetectionRepository.existsByAnalysisJobIdAndKeyword(entity.id, keyword)) {
                    keywordDetectionRepository.save(
                        KeywordDetectionEntity(
                            analysisJobId = entity.id,
                            keyword = keyword,
                            confidence = 1.0
                        )
                    )
                }
            }
    }

    private fun analyzeDocument(documentId: String, ownerUserId: String): LocalAnalysisResult {
        val document = documentLookupPort.get(documentId, ownerUserId)
        val textContent = documentLookupPort.readTextContent(documentId, ownerUserId)
        val searchTarget = listOfNotNull(textContent, document.filename, document.fileKey)
            .joinToString(" ")
            .lowercase()
        if (LOCAL_FAILURE_MARKERS.any { marker -> searchTarget.contains(marker) }) {
            throw LocalAnalysisFailedException("로컬 분석 실패 마커가 감지되었습니다.")
        }

        val detectedKeywords = LOCAL_KEYWORD_RULES
            .filter { rule -> rule.aliases.any { alias -> searchTarget.contains(alias.lowercase()) } }
            .map { it.keyword }
            .ifEmpty { listOf("검토") }
        val riskScore = calculateRiskScore(detectedKeywords, textContent)
        val basis = if (textContent.isNullOrBlank()) "문서 메타데이터" else "업로드된 텍스트 파일 내용"

        return LocalAnalysisResult(
            summary = "로컬 분석이 완료되었습니다. $basis 기준으로 ${detectedKeywords.joinToString(", ")} 키워드를 감지했습니다.",
            riskScore = riskScore,
            keywords = detectedKeywords
        )
    }

    private fun calculateRiskScore(keywords: List<String>, textContent: String?): Int {
        val baseScore = if (textContent.isNullOrBlank()) 20 else 30
        val keywordScore = keywords.size * 12
        val urgentScore = if (keywords.any { it in setOf("긴급", "위험", "개인정보") }) 20 else 0
        return (baseScore + keywordScore + urgentScore).coerceAtMost(100)
    }

    private fun keywordsFor(entity: AnalysisJobEntity): List<String> {
        val detected = keywordDetectionRepository.findByAnalysisJobId(entity.id)
            .sortedBy { it.createdAt }
            .map { it.keyword }

        if (detected.isNotEmpty()) {
            return detected
        }

        return entity.keywords.split(",").filter { it.isNotBlank() }
    }

    private fun toDocumentStatus(analysisState: String): String =
        when (analysisState) {
            "QUEUED" -> "ANALYSIS_QUEUED"
            "PROCESSING" -> "ANALYSIS_PROCESSING"
            "COMPLETED" -> "ANALYSIS_COMPLETED"
            "FAILED" -> "ANALYSIS_FAILED"
            else -> "ANALYSIS_QUEUED"
        }

    private fun toResponse(entity: AnalysisJobEntity): AnalysisJobResponse =
        AnalysisJobResponse(
            jobId = entity.id,
            ownerUserId = entity.ownerUserId,
            documentId = entity.documentId,
            state = entity.state,
            createdAt = entity.createdAt,
            analysisProvider = entity.analysisProvider,
            resultSummary = entity.resultSummary,
            riskScore = entity.riskScore,
            keywords = keywordsFor(entity),
            errorCode = entity.errorCode,
            errorMessage = entity.errorMessage,
            failedAt = entity.failedAt
        )

    private data class LocalKeywordRule(
        val keyword: String,
        val aliases: List<String>
    )

    private data class LocalAnalysisResult(
        val summary: String,
        val riskScore: Int,
        val keywords: List<String>
    )

    private companion object {
        val LOCAL_KEYWORD_RULES = listOf(
            LocalKeywordRule("계약", listOf("계약", "contract")),
            LocalKeywordRule("검토", listOf("검토", "review")),
            LocalKeywordRule("알림", listOf("알림", "notification")),
            LocalKeywordRule("긴급", listOf("긴급", "urgent")),
            LocalKeywordRule("위험", listOf("위험", "risk")),
            LocalKeywordRule("청구", listOf("청구", "invoice", "billing")),
            LocalKeywordRule("개인정보", listOf("개인정보", "privacy", "personal"))
        )
        val LOCAL_FAILURE_MARKERS = listOf("분석실패", "fail", "analysis-fail", "force-fail")
    }
}

@RestController
@RequestMapping("/api/v1/analysis/jobs")
class AnalysisController(
    private val analysisJobService: AnalysisJobService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createJob(
        @RequestBody request: AnalysisJobCreateRequest,
        servletRequest: HttpServletRequest
    ): AnalysisJobResponse {
        require(request.documentId.isNotBlank()) { "documentId must not be blank" }
        return analysisJobService.create(request, ownerUserIdFrom(servletRequest))
    }

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: String, servletRequest: HttpServletRequest): AnalysisJobResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return analysisJobService.get(id, ownerUserIdFrom(servletRequest))
    }

    @PostMapping("/{id}/retry")
    fun retryJob(@PathVariable id: String, servletRequest: HttpServletRequest): AnalysisJobResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return analysisJobService.retry(id, ownerUserIdFrom(servletRequest))
    }
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidationError(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "VALIDATION_ERROR",
                message = ex.message ?: "validation failed",
                traceId = UUID.randomUUID().toString()
            )
        )

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(
        ex: ResourceNotFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "RESOURCE_NOT_FOUND",
                message = ex.message ?: "resource not found",
                traceId = UUID.randomUUID().toString()
            )
        )

    @ExceptionHandler(ExternalServiceException::class)
    fun handleExternalServiceError(
        ex: ExternalServiceException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "UPSTREAM_DOCUMENT_ERROR",
                message = ex.message ?: "upstream document service error",
                traceId = UUID.randomUUID().toString()
            )
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedError(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "INTERNAL_ERROR",
                message = ex.message ?: "unexpected server error",
                traceId = UUID.randomUUID().toString()
            )
        )
}
