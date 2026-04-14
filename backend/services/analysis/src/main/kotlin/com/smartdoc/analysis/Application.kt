package com.smartdoc.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.servlet.http.HttpServletRequest
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
    val documentId: String,
    val state: String,
    val createdAt: Instant,
    val analysisProvider: String
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
    fun exists(documentId: String): Boolean
    fun updateStatus(documentId: String, status: String)
}

@Service
@Profile("local")
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
@Profile("local")
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

    override fun exists(documentId: String): Boolean = try {
        client.get()
            .uri("/api/v1/documents/{id}", documentId)
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

    override fun updateStatus(documentId: String, status: String) {
        try {
            client.post()
                .uri("/api/v1/documents/{id}/status", documentId)
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
    override fun exists(documentId: String): Boolean = true
    override fun updateStatus(documentId: String, status: String) = Unit
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

@Entity
@Table(name = "analysis_jobs")
class AnalysisJobEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "document_id", nullable = false, length = 36)
    var documentId: String = "",

    @Column(nullable = false, length = 32)
    var state: String = "QUEUED",

    @Column(name = "analysis_provider", nullable = false, length = 64)
    var analysisProvider: String = "local-stub",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
    }
}

interface AnalysisJobRepository : JpaRepository<AnalysisJobEntity, String>

@Service
class AnalysisJobService(
    private val aiAnalysisPort: AiAnalysisPort,
    private val documentLookupPort: DocumentLookupPort,
    private val analysisJobRepository: AnalysisJobRepository
) {
    fun create(request: AnalysisJobCreateRequest): AnalysisJobResponse {
        val docId = request.documentId.trim()
        if (!documentLookupPort.exists(docId)) {
            throw ResourceNotFoundException("document not found: $docId")
        }

        val result = aiAnalysisPort.submit(AiAnalysisCommand(documentId = docId))

        val saved = analysisJobRepository.save(
            AnalysisJobEntity(
                documentId = docId,
                state = result.state,
                analysisProvider = result.provider
            )
        )
        documentLookupPort.updateStatus(docId, toDocumentStatus(saved.state))
        return toResponse(saved)
    }

    fun get(jobId: String): AnalysisJobResponse =
        analysisJobRepository.findById(jobId)
            .map(::advanceLocalState)
            .map(::toResponse)
            .orElseThrow { ResourceNotFoundException("analysis job not found: $jobId") }

    private fun advanceLocalState(entity: AnalysisJobEntity): AnalysisJobEntity {
        if (entity.state == "COMPLETED") {
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
        val saved = analysisJobRepository.save(entity)
        documentLookupPort.updateStatus(saved.documentId, toDocumentStatus(saved.state))
        return saved
    }

    private fun toDocumentStatus(analysisState: String): String =
        when (analysisState) {
            "QUEUED" -> "ANALYSIS_QUEUED"
            "PROCESSING" -> "ANALYSIS_PROCESSING"
            "COMPLETED" -> "ANALYSIS_COMPLETED"
            else -> "ANALYSIS_QUEUED"
        }

    private fun toResponse(entity: AnalysisJobEntity): AnalysisJobResponse =
        AnalysisJobResponse(
            jobId = entity.id,
            documentId = entity.documentId,
            state = entity.state,
            createdAt = entity.createdAt,
            analysisProvider = entity.analysisProvider
        )
}

@RestController
@RequestMapping("/api/v1/analysis/jobs")
class AnalysisController(
    private val analysisJobService: AnalysisJobService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createJob(@RequestBody request: AnalysisJobCreateRequest): AnalysisJobResponse {
        require(request.documentId.isNotBlank()) { "documentId must not be blank" }
        return analysisJobService.create(request)
    }

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: String): AnalysisJobResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return analysisJobService.get(id)
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
