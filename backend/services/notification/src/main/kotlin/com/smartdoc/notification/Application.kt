package com.smartdoc.notification

import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
        "service" to "notification",
        "status" to "ok"
    )
}

data class NotificationDispatchRequest(
    val documentId: String,
    val channel: String,
    val message: String
)

data class NotificationEventResponse(
    val eventId: String,
    val documentId: String,
    val channel: String,
    val message: String,
    val status: String,
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

@Service
class NotificationMemoryStore {
    private val storage = ConcurrentHashMap<String, NotificationEventResponse>()

    fun create(request: NotificationDispatchRequest): NotificationEventResponse {
        val created = NotificationEventResponse(
            eventId = UUID.randomUUID().toString(),
            documentId = request.documentId.trim(),
            channel = request.channel.trim().lowercase(),
            message = request.message.trim(),
            status = "DISPATCHED",
            createdAt = Instant.now()
        )
        storage[created.eventId] = created
        return created
    }

    fun get(eventId: String): NotificationEventResponse =
        storage[eventId] ?: throw ResourceNotFoundException("notification event not found: $eventId")

    fun list(): List<NotificationEventResponse> =
        storage.values.sortedByDescending { it.createdAt }
}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val store: NotificationMemoryStore
) {
    @PostMapping("/dispatch")
    @ResponseStatus(HttpStatus.CREATED)
    fun dispatch(@RequestBody request: NotificationDispatchRequest): NotificationEventResponse {
        require(request.documentId.isNotBlank()) { "documentId must not be blank" }
        require(request.channel.isNotBlank()) { "channel must not be blank" }
        require(request.message.isNotBlank()) { "message must not be blank" }
        return store.create(request)
    }

    @GetMapping("/events/{id}")
    fun getEvent(@PathVariable id: String): NotificationEventResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return store.get(id)
    }

    @GetMapping("/events")
    fun listEvents(): List<NotificationEventResponse> = store.list()
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
