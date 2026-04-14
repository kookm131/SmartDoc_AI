package com.smartdoc.analysis

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    val createdAt: Instant
)

data class ApiErrorResponse(
    val timestamp: Instant,
    val path: String,
    val code: String,
    val message: String,
    val traceId: String
)

class ResourceNotFoundException(message: String) : RuntimeException(message)

class AnalysisJobMemoryStore {
    private val storage = ConcurrentHashMap<String, AnalysisJobResponse>()

    fun create(request: AnalysisJobCreateRequest): AnalysisJobResponse {
        val created = AnalysisJobResponse(
            jobId = UUID.randomUUID().toString(),
            documentId = request.documentId.trim(),
            state = "QUEUED",
            createdAt = Instant.now()
        )
        storage[created.jobId] = created
        return created
    }

    fun get(jobId: String): AnalysisJobResponse =
        storage[jobId] ?: throw ResourceNotFoundException("analysis job not found: $jobId")
}

@RestController
@RequestMapping("/api/v1/analysis/jobs")
class AnalysisController {
    private val store = AnalysisJobMemoryStore()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createJob(@RequestBody request: AnalysisJobCreateRequest): AnalysisJobResponse {
        require(request.documentId.isNotBlank()) { "documentId must not be blank" }
        return store.create(request)
    }

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: String): AnalysisJobResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return store.get(id)
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
}
