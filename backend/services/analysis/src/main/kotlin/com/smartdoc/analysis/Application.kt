package com.smartdoc.analysis

import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
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
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.comprehend.ComprehendClient
import software.amazon.awssdk.services.comprehend.model.DetectKeyPhrasesRequest
import software.amazon.awssdk.services.comprehend.model.LanguageCode
import software.amazon.awssdk.services.textract.TextractClient
import software.amazon.awssdk.services.textract.model.BlockType
import software.amazon.awssdk.services.textract.model.DocumentLocation
import software.amazon.awssdk.services.textract.model.GetDocumentTextDetectionRequest
import software.amazon.awssdk.services.textract.model.JobStatus
import software.amazon.awssdk.services.textract.model.S3Object
import software.amazon.awssdk.services.textract.model.StartDocumentTextDetectionRequest
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

private const val SMARTDOC_TRACE_ID_HEADER = "X-SmartDoc-Trace-Id"
private const val TRACE_ID_ATTRIBUTE = "smartdoc.traceId"

private fun traceIdFrom(request: HttpServletRequest): String =
    (request.getAttribute(TRACE_ID_ATTRIBUTE) as? String)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: request.getHeader(SMARTDOC_TRACE_ID_HEADER)?.trim()?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString()

private fun currentTraceId(): String = MDC.get("traceId")?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val traceId = request.getHeader(SMARTDOC_TRACE_ID_HEADER)?.trim()?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId)
        response.setHeader(SMARTDOC_TRACE_ID_HEADER, traceId)
        MDC.put("traceId", traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("traceId")
        }
    }
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
    val resultDetails: AnalysisResultDetails?,
    val riskScore: Int?,
    val keywords: List<String>,
    val errorCode: String?,
    val errorMessage: String?,
    val failedAt: Instant?
)

data class AnalysisResultDetails(
    val basis: String,
    val completeness: String = "FULL",
    val extraction: AnalysisExtractionDetails? = null,
    val summary: AnalysisStructuredSummary? = null,
    val risk: AnalysisRiskDetails? = null,
    val highlights: List<String> = emptyList(),
    val signals: List<String> = emptyList()
)

data class AnalysisExtractionDetails(
    val status: String,
    val contentType: String? = null,
    val textChars: Int? = null,
    val note: String? = null
)

data class AnalysisStructuredSummary(
    val title: String,
    val bullets: List<String> = emptyList()
)

data class AnalysisRiskDetails(
    val baseScore: Int,
    val keywordScore: Int,
    val urgentScore: Int,
    val cappedScore: Int,
    val level: String
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

interface TextExtractionPort {
    fun extractText(document: DocumentInfo, ownerUserId: String): String?
}

interface KeywordEnrichmentPort {
    fun enrichSignals(textContent: String?, document: DocumentInfo): List<String>
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
@Profile("local | mariadb | aws")
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
            .header(SMARTDOC_TRACE_ID_HEADER, currentTraceId())
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
            .header(SMARTDOC_TRACE_ID_HEADER, currentTraceId())
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
            .header(SMARTDOC_TRACE_ID_HEADER, currentTraceId())
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
                .header(SMARTDOC_TRACE_ID_HEADER, currentTraceId())
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
@Profile("aws-stub")
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
class LocalTextExtractionAdapter(
    private val documentLookupPort: DocumentLookupPort
) : TextExtractionPort {
    override fun extractText(document: DocumentInfo, ownerUserId: String): String? =
        documentLookupPort.readTextContent(document.documentId, ownerUserId)
}

@Service
@Profile("aws")
class AwsTextractTextExtractionAdapter(
    private val documentLookupPort: DocumentLookupPort,
    @Value("\${SMARTDOC_TEXTRACT_ENABLED:false}")
    private val enabled: Boolean,
    @Value("\${SMARTDOC_S3_BUCKET:smartdoc-local}")
    private val s3Bucket: String,
    @Value("\${SMARTDOC_AWS_REGION:us-east-1}")
    private val region: String,
    @Value("\${SMARTDOC_AWS_TEXTRACT_ENDPOINT:}")
    private val textractEndpoint: String,
    @Value("\${SMARTDOC_TEXTRACT_POLL_MS:750}")
    private val pollMs: Long,
    @Value("\${SMARTDOC_TEXTRACT_MAX_WAIT_MS:15000}")
    private val maxWaitMs: Long,
    @Value("\${AWS_ACCESS_KEY_ID:}")
    private val accessKeyId: String,
    @Value("\${AWS_SECRET_ACCESS_KEY:}")
    private val secretAccessKey: String
) : TextExtractionPort {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val maxChars = 20_000

    private val textract: TextractClient = run {
        val builder = TextractClient.builder()
            .region(Region.of(region.trim().ifBlank { "us-east-1" }))

        val endpoint = textractEndpoint.trim()
        if (endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(endpoint))
        }

        if (accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())
                )
            )
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create())
        }

        builder.build()
    }

    override fun extractText(document: DocumentInfo, ownerUserId: String): String? {
        // Keep aws profile safe by default: disabled -> fallback to document service.
        if (!enabled) {
            return documentLookupPort.readTextContent(document.documentId, ownerUserId)
        }

        val contentType = document.contentType?.trim()
        if (contentType != "application/pdf") {
            return documentLookupPort.readTextContent(document.documentId, ownerUserId)
        }

        val key = document.fileKey.trim()
        if (key.isBlank()) {
            return documentLookupPort.readTextContent(document.documentId, ownerUserId)
        }

        return try {
            val jobId = textract.startDocumentTextDetection(
                StartDocumentTextDetectionRequest.builder()
                    .documentLocation(
                        DocumentLocation.builder()
                            .s3Object(
                                S3Object.builder()
                                    .bucket(s3Bucket.trim().ifBlank { "smartdoc-local" })
                                    .name(key)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            ).jobId()

            val deadline = System.currentTimeMillis() + maxWaitMs.coerceAtLeast(1000)
            var nextToken: String? = null
            var lastStatus: JobStatus? = null
            val lines = mutableListOf<String>()

            while (System.currentTimeMillis() < deadline) {
                val resp = textract.getDocumentTextDetection(
                    GetDocumentTextDetectionRequest.builder()
                        .jobId(jobId)
                        .nextToken(nextToken)
                        .build()
                )

                val status = resp.jobStatus()
                lastStatus = status
                if (status == JobStatus.FAILED || status == JobStatus.PARTIAL_SUCCESS) {
                    break
                }

                if (status == JobStatus.SUCCEEDED) {
                    resp.blocks()
                        ?.asSequence()
                        ?.filter { it.blockType() == BlockType.LINE }
                        ?.mapNotNull { it.text()?.trim()?.takeIf { t -> t.isNotBlank() } }
                        ?.forEach { text ->
                            if (lines.sumOf { it.length } < maxChars) {
                                lines.add(text)
                            }
                        }
                    nextToken = resp.nextToken()
                    if (nextToken.isNullOrBlank()) {
                        break
                    }
                    continue
                }

                Thread.sleep(pollMs.coerceIn(200, 2000))
            }

            val combined = lines.joinToString("\n").trim().takeIf { it.isNotBlank() }
            if (combined != null) {
                combined.take(maxChars)
            } else {
                logger.warn("textract returned no text (status={}) for key={}", lastStatus, key)
                documentLookupPort.readTextContent(document.documentId, ownerUserId)
            }
        } catch (ex: Exception) {
            logger.warn("textract extraction failed for key={}: {}", key, ex.message)
            documentLookupPort.readTextContent(document.documentId, ownerUserId)
        }
    }
}

@Service
@Profile("local | mariadb")
class LocalKeywordEnrichmentAdapter : KeywordEnrichmentPort {
    override fun enrichSignals(textContent: String?, document: DocumentInfo): List<String> = emptyList()
}

@Service
@Profile("aws")
class AwsComprehendKeywordEnrichmentAdapter(
    @Value("\${SMARTDOC_COMPREHEND_ENABLED:false}")
    private val enabled: Boolean,
    @Value("\${SMARTDOC_AWS_REGION:us-east-1}")
    private val region: String,
    @Value("\${SMARTDOC_AWS_COMPREHEND_ENDPOINT:}")
    private val comprehendEndpoint: String,
    @Value("\${SMARTDOC_COMPREHEND_MAX_SIGNALS:8}")
    private val maxSignals: Int,
    @Value("\${SMARTDOC_COMPREHEND_MIN_SCORE:0.35}")
    private val minScore: Double,
    @Value("\${AWS_ACCESS_KEY_ID:}")
    private val accessKeyId: String,
    @Value("\${AWS_SECRET_ACCESS_KEY:}")
    private val secretAccessKey: String
) : KeywordEnrichmentPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val comprehend: ComprehendClient = run {
        val builder = ComprehendClient.builder()
            .region(Region.of(region.trim().ifBlank { "us-east-1" }))

        val endpoint = comprehendEndpoint.trim()
        if (endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(endpoint))
        }

        if (accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())
                )
            )
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create())
        }

        builder.build()
    }

    override fun enrichSignals(textContent: String?, document: DocumentInfo): List<String> {
        if (!enabled) return emptyList()
        val text = textContent?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()

        // Comprehend synchronous APIs have small payload limits; keep it conservative.
        val input = text.take(4500)
        val language = if (input.any { it in '\uAC00'..'\uD7A3' }) LanguageCode.KO else LanguageCode.EN

        return try {
            val resp = comprehend.detectKeyPhrases(
                DetectKeyPhrasesRequest.builder()
                    .languageCode(language)
                    .text(input)
                    .build()
            )

            resp.keyPhrases()
                ?.asSequence()
                ?.filter { it.score() >= minScore }
                ?.sortedByDescending { it.score() }
                ?.mapNotNull { it.text()?.replace('\n', ' ')?.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.take(maxSignals.coerceIn(0, 20))
                ?.map { phrase -> "comprehend:keyPhrase:${phrase.take(64)}" }
                ?.toList()
                ?: emptyList()
        } catch (ex: Exception) {
            logger.warn("comprehend enrich failed: {}", ex.message)
            emptyList()
        }
    }
}

@Service
@Profile("local | mariadb | aws")
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
                .header(SMARTDOC_TRACE_ID_HEADER, currentTraceId())
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
@Profile("aws-stub")
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

    @Lob
    @Column(name = "result_details_json", columnDefinition = "LONGTEXT")
    var resultDetailsJson: String? = null,

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
    fun findFirstByOwnerUserIdAndDocumentIdOrderByCreatedAtDesc(ownerUserId: String, documentId: String): AnalysisJobEntity?
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
    private val textExtractionPort: TextExtractionPort,
    private val keywordEnrichmentPort: KeywordEnrichmentPort,
    private val notificationDispatchPort: NotificationDispatchPort,
    private val analysisJobRepository: AnalysisJobRepository,
    private val keywordDetectionRepository: KeywordDetectionRepository,
    private val objectMapper: ObjectMapper
) {
    fun create(request: AnalysisJobCreateRequest, ownerUserId: String): AnalysisJobResponse {
        val docId = request.documentId.trim()
        if (!documentLookupPort.exists(docId, ownerUserId)) {
            throw ResourceNotFoundException("document not found: $docId")
        }

        // Prevent duplicate jobs by reusing the latest job per (owner, document).
        // If the latest job is FAILED, treat "create" as a convenience retry.
        analysisJobRepository.findFirstByOwnerUserIdAndDocumentIdOrderByCreatedAtDesc(ownerUserId, docId)
            ?.let { latest ->
                return if (latest.state == "FAILED") {
                    retry(latest.id, ownerUserId)
                } else {
                    toResponse(advanceLocalState(latest))
                }
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
        found.resultDetailsJson = null
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
                entity.resultDetailsJson = writeResultDetailsJson(result.details)
                entity.riskScore = result.riskScore
                entity.keywords = result.keywords.joinToString(",")
                entity.errorCode = null
                entity.errorMessage = null
                entity.failedAt = null
            } catch (ex: LocalAnalysisFailedException) {
                entity.state = "FAILED"
                entity.resultSummary = null
                entity.resultDetailsJson = null
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
        val textContent = textExtractionPort.extractText(document, ownerUserId)
        val enrichmentSignals = keywordEnrichmentPort.enrichSignals(textContent, document)
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
        val risk = calculateRisk(detectedKeywords, textContent)
        val basis = if (textContent.isNullOrBlank()) "METADATA" else "CONTENT"
        val basisText = if (basis == "CONTENT") "업로드된 파일 내용" else "문서 메타데이터"
        val completeness = if (basis == "CONTENT") "FULL" else "PARTIAL"
        val extractionStatus = when {
            basis == "CONTENT" -> "SUCCESS"
            document.contentType in setOf("text/plain", "application/pdf") -> "EMPTY"
            else -> "UNAVAILABLE"
        }
        val extractionNote = when (extractionStatus) {
            "SUCCESS" -> null
            "EMPTY" -> "텍스트를 추출하지 못했거나 내용이 비어있습니다."
            else -> "이 contentType에서는 텍스트 추출을 제공하지 않습니다."
        }
        val riskText = "${risk.cappedScore}점(${risk.level})"

        return LocalAnalysisResult(
            summary = "로컬 분석이 완료되었습니다. $basisText 기준으로 ${detectedKeywords.joinToString(", ")} 키워드를 감지했습니다.",
            riskScore = risk.cappedScore,
            keywords = detectedKeywords,
            details = AnalysisResultDetails(
                basis = basis,
                completeness = completeness,
                extraction = AnalysisExtractionDetails(
                    status = extractionStatus,
                    contentType = document.contentType,
                    textChars = textContent?.length,
                    note = extractionNote
                ),
                summary = AnalysisStructuredSummary(
                    title = "분석 요약",
                    bullets = listOf(
                        "분석 기준: $basisText",
                        "감지 키워드: ${detectedKeywords.joinToString(", ")}",
                        "위험 점수: $riskText"
                    )
                ),
                risk = risk,
                highlights = listOf(
                    "detectedKeywords=${detectedKeywords.joinToString(",")}",
                    "riskScore=${risk.cappedScore}"
                ),
                signals = detectedKeywords.map { "keyword:$it" } + listOf(
                    "completeness=$completeness",
                    "extractionStatus=$extractionStatus",
                    "riskLevel=${risk.level}"
                ) + enrichmentSignals
            )
        )
    }

    private fun writeResultDetailsJson(details: AnalysisResultDetails): String =
        detailsJsonWriter()
            .writeValueAsString(details)
            .takeIf { it.length <= MAX_RESULT_DETAILS_JSON_CHARS }
            ?: detailsJsonWriter().writeValueAsString(
                mapOf(
                    "basis" to details.basis,
                    "completeness" to details.completeness,
                    "highlights" to details.highlights
                )
            )

    private fun detailsJsonWriter() =
        objectMapper.writer()
            .with(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature())

    private fun calculateRisk(keywords: List<String>, textContent: String?): AnalysisRiskDetails {
        val baseScore = if (textContent.isNullOrBlank()) 20 else 30
        val keywordScore = keywords.size * 12
        val urgentScore = if (keywords.any { it in setOf("긴급", "위험", "개인정보") }) 20 else 0
        val capped = (baseScore + keywordScore + urgentScore).coerceAtMost(100)
        val level = when {
            capped >= 70 -> "HIGH"
            capped >= 40 -> "MEDIUM"
            else -> "LOW"
        }
        return AnalysisRiskDetails(
            baseScore = baseScore,
            keywordScore = keywordScore,
            urgentScore = urgentScore,
            cappedScore = capped,
            level = level
        )
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

    private fun toResponse(entity: AnalysisJobEntity): AnalysisJobResponse {
        val parsedDetails = entity.resultDetailsJson?.let { json ->
            try {
                objectMapper.readValue(json, AnalysisResultDetails::class.java)
            } catch (_: Exception) {
                null
            }
        }

        return AnalysisJobResponse(
            jobId = entity.id,
            ownerUserId = entity.ownerUserId,
            documentId = entity.documentId,
            state = entity.state,
            createdAt = entity.createdAt,
            analysisProvider = entity.analysisProvider,
            resultSummary = entity.resultSummary,
            resultDetails = compatibleResultDetails(entity, parsedDetails),
            riskScore = entity.riskScore,
            keywords = keywordsFor(entity),
            errorCode = entity.errorCode,
            errorMessage = entity.errorMessage,
            failedAt = entity.failedAt
        )
    }

    private fun compatibleResultDetails(
        entity: AnalysisJobEntity,
        details: AnalysisResultDetails?
    ): AnalysisResultDetails? {
        if (entity.state != "COMPLETED" || entity.resultSummary.isNullOrBlank()) {
            return details
        }

        val keywords = keywordsFor(entity)
        val inferredBasis = if (entity.resultSummary?.contains("업로드된 파일 내용") == true) "CONTENT" else "METADATA"
        val basis = details?.basis ?: inferredBasis
        val completeness = details?.completeness ?: if (basis == "CONTENT") "FULL" else "PARTIAL"
        val extractionStatus = details?.extraction?.status ?: if (basis == "CONTENT") "SUCCESS" else "EMPTY"
        val risk = details?.risk ?: entity.riskScore?.let { score ->
            AnalysisRiskDetails(
                baseScore = 0,
                keywordScore = 0,
                urgentScore = 0,
                cappedScore = score,
                level = riskLevel(score)
            )
        }

        return AnalysisResultDetails(
            basis = basis,
            completeness = completeness,
            extraction = details?.extraction ?: AnalysisExtractionDetails(
                status = extractionStatus,
                note = if (extractionStatus == "SUCCESS") null else "이전 분석 결과라 텍스트 추출 상세 정보가 제한됩니다."
            ),
            summary = details?.summary ?: AnalysisStructuredSummary(
                title = "분석 요약",
                bullets = listOf(
                    "분석 기준: ${if (basis == "CONTENT") "업로드된 파일 내용" else "문서 메타데이터"}",
                    "감지 키워드: ${keywords.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "없음"}",
                    "위험 점수: ${entity.riskScore?.let { "${it}점(${riskLevel(it)})" } ?: "미정"}"
                )
            ),
            risk = risk,
            highlights = details?.highlights ?: emptyList(),
            signals = details?.signals ?: emptyList()
        )
    }

    private fun riskLevel(score: Int): String =
        when {
            score >= 70 -> "HIGH"
            score >= 40 -> "MEDIUM"
            else -> "LOW"
        }

    private data class LocalKeywordRule(
        val keyword: String,
        val aliases: List<String>
    )

    private data class LocalAnalysisResult(
        val summary: String,
        val riskScore: Int,
        val keywords: List<String>,
        val details: AnalysisResultDetails
    )

    private companion object {
        val LOCAL_KEYWORD_RULES = listOf(
            LocalKeywordRule("계약", listOf("계약", "contract")),
            LocalKeywordRule("검토", listOf("검토", "review")),
            LocalKeywordRule("알림", listOf("알림", "notification")),
            LocalKeywordRule("긴급", listOf("긴급", "urgent")),
            LocalKeywordRule("위험", listOf("위험", "risk")),
            LocalKeywordRule("청구", listOf("청구", "invoice", "billing")),
            LocalKeywordRule("개인정보", listOf("개인정보", "privacy", "personal")),

            // Contract / compliance signals
            LocalKeywordRule("해지", listOf("해지", "termination", "cancel")),
            LocalKeywordRule("위약금", listOf("위약금", "penalty", "liquidated damages")),
            LocalKeywordRule("자동갱신", listOf("자동갱신", "auto-renew", "auto renew")),
            LocalKeywordRule("수수료", listOf("수수료", "fee")),
            LocalKeywordRule("지급", listOf("지급", "payment")),
            LocalKeywordRule("기한", listOf("기한", "deadline", "due date")),
            LocalKeywordRule("비밀유지", listOf("비밀유지", "nda", "confidential")),
            LocalKeywordRule("SLA", listOf("sla", "service level"))
        )
        val LOCAL_FAILURE_MARKERS = listOf("분석실패", "fail", "analysis-fail", "force-fail")
        const val MAX_RESULT_DETAILS_JSON_CHARS = 240
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
    private val logger = LoggerFactory.getLogger(javaClass)

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
                traceId = traceIdFrom(request)
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
                traceId = traceIdFrom(request)
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
                traceId = traceIdFrom(request)
            )
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedError(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val traceId = traceIdFrom(request)
        logger.error("unexpected analysis service error traceId={} path={}", traceId, request.requestURI, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "INTERNAL_ERROR",
                message = "${ex.javaClass.simpleName}: ${ex.message ?: "unexpected server error"}",
                traceId = traceId
            )
        )
    }
}
