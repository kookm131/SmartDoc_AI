package com.smartdoc.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.JpaRepository
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

@Entity
@Table(name = "notification_events")
class NotificationEventEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "document_id", nullable = false, length = 36)
    var documentId: String = "",

    @Column(nullable = false, length = 32)
    var channel: String = "",

    @Column(nullable = false, length = 1024)
    var message: String = "",

    @Column(nullable = false, length = 32)
    var status: String = "DISPATCHED",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
    }
}

interface NotificationEventRepository : JpaRepository<NotificationEventEntity, String>

@Service
class NotificationService(
    private val notificationEventRepository: NotificationEventRepository
) {
    fun create(request: NotificationDispatchRequest): NotificationEventResponse {
        val saved = notificationEventRepository.save(
            NotificationEventEntity(
                documentId = request.documentId.trim(),
                channel = request.channel.trim().lowercase(),
                message = request.message.trim(),
                status = "DISPATCHED"
            )
        )
        return toResponse(saved)
    }

    fun get(eventId: String): NotificationEventResponse =
        notificationEventRepository.findById(eventId)
            .map(::toResponse)
            .orElseThrow { ResourceNotFoundException("notification event not found: $eventId") }

    fun list(): List<NotificationEventResponse> =
        notificationEventRepository.findAll()
            .sortedByDescending { it.createdAt }
            .map(::toResponse)

    private fun toResponse(entity: NotificationEventEntity): NotificationEventResponse =
        NotificationEventResponse(
            eventId = entity.id,
            documentId = entity.documentId,
            channel = entity.channel,
            message = entity.message,
            status = entity.status,
            createdAt = entity.createdAt
        )
}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    @PostMapping("/dispatch")
    @ResponseStatus(HttpStatus.CREATED)
    fun dispatch(@RequestBody request: NotificationDispatchRequest): NotificationEventResponse {
        require(request.documentId.isNotBlank()) { "documentId must not be blank" }
        require(request.channel.isNotBlank()) { "channel must not be blank" }
        require(request.message.isNotBlank()) { "message must not be blank" }
        return notificationService.create(request)
    }

    @GetMapping("/events/{id}")
    fun getEvent(@PathVariable id: String): NotificationEventResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return notificationService.get(id)
    }

    @GetMapping("/events")
    fun listEvents(): List<NotificationEventResponse> = notificationService.list()
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
