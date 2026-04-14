package com.smartdoc.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
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
class Application {
    @Bean
    fun seedNotificationRules(notificationRuleRepository: NotificationRuleRepository) = CommandLineRunner {
        if (!notificationRuleRepository.existsByKeywordAndChannel("계약", "slack")) {
            notificationRuleRepository.save(
                NotificationRuleEntity(
                    keyword = "계약",
                    channel = "slack",
                    enabled = true
                )
            )
        }
    }
}

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
    val channel: String? = null,
    val message: String? = null,
    val keywords: List<String> = emptyList(),
    val riskScore: Int? = null
)

data class NotificationEventResponse(
    val eventId: String,
    val documentId: String,
    val channel: String,
    val message: String,
    val status: String,
    val createdAt: Instant
)

data class NotificationRuleCreateRequest(
    val keyword: String,
    val channel: String,
    val enabled: Boolean = true
)

data class NotificationRuleResponse(
    val ruleId: String,
    val keyword: String,
    val channel: String,
    val enabled: Boolean,
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

@Entity
@Table(
    name = "notification_rules",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_rules_keyword_channel",
            columnNames = ["keyword", "channel"]
        )
    ]
)
class NotificationRuleEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 128)
    var keyword: String = "",

    @Column(nullable = false, length = 32)
    var channel: String = "",

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
) {
    @PrePersist
    fun onCreate() {
        createdAt = Instant.now()
    }
}

interface NotificationRuleRepository : JpaRepository<NotificationRuleEntity, String> {
    fun existsByKeywordAndChannel(keyword: String, channel: String): Boolean
    fun findByKeywordAndChannel(keyword: String, channel: String): NotificationRuleEntity?
    fun findByEnabledTrue(): List<NotificationRuleEntity>
}

@Service
class NotificationService(
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationRuleRepository: NotificationRuleRepository
) {
    fun create(request: NotificationDispatchRequest): NotificationEventResponse? {
        if (request.keywords.isNotEmpty()) {
            val matchingRule = notificationRuleRepository.findByEnabledTrue()
                .firstOrNull { rule -> request.keywords.any { it == rule.keyword } }
                ?: return null

            return saveEvent(
                documentId = request.documentId.trim(),
                channel = matchingRule.channel,
                message = analysisCompletedMessage(
                    keyword = matchingRule.keyword,
                    riskScore = request.riskScore
                )
            )
        }

        return saveEvent(
            documentId = request.documentId.trim(),
            channel = request.channel?.trim()?.lowercase() ?: "",
            message = request.message?.trim() ?: ""
        )
    }

    fun createRule(request: NotificationRuleCreateRequest): NotificationRuleResponse {
        val keyword = request.keyword.trim()
        val channel = request.channel.trim().lowercase()
        val rule = notificationRuleRepository.findByKeywordAndChannel(keyword, channel)
            ?.also { it.enabled = request.enabled }
            ?: NotificationRuleEntity(
                keyword = keyword,
                channel = channel,
                enabled = request.enabled
            )
        val saved = notificationRuleRepository.save(rule)
        return toRuleResponse(saved)
    }

    fun listRules(): List<NotificationRuleResponse> =
        notificationRuleRepository.findAll()
            .sortedWith(compareBy<NotificationRuleEntity> { it.keyword }.thenBy { it.channel })
            .map(::toRuleResponse)

    private fun saveEvent(
        documentId: String,
        channel: String,
        message: String
    ): NotificationEventResponse {
        val saved = notificationEventRepository.save(
            NotificationEventEntity(
                documentId = documentId,
                channel = channel,
                message = message,
                status = "DISPATCHED"
            )
        )
        return toResponse(saved)
    }

    private fun analysisCompletedMessage(keyword: String, riskScore: Int?): String {
        val score = riskScore?.let { "위험 점수 ${it}점" } ?: "위험 점수 미정"
        return "분석 완료: '$keyword' 키워드 규칙이 매칭되었습니다. $score"
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

    private fun toRuleResponse(entity: NotificationRuleEntity): NotificationRuleResponse =
        NotificationRuleResponse(
            ruleId = entity.id,
            keyword = entity.keyword,
            channel = entity.channel,
            enabled = entity.enabled,
            createdAt = entity.createdAt
        )
}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {
    @PostMapping("/dispatch")
    fun dispatch(@RequestBody request: NotificationDispatchRequest): ResponseEntity<NotificationEventResponse> {
        require(request.documentId.isNotBlank()) { "documentId must not be blank" }

        if (request.keywords.isEmpty()) {
            require(!request.channel.isNullOrBlank()) { "channel must not be blank" }
            require(!request.message.isNullOrBlank()) { "message must not be blank" }
        }

        val event = notificationService.create(request)
            ?: return ResponseEntity.noContent().build()

        return ResponseEntity.status(HttpStatus.CREATED).body(event)
    }

    @GetMapping("/events/{id}")
    fun getEvent(@PathVariable id: String): NotificationEventResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return notificationService.get(id)
    }

    @GetMapping("/events")
    fun listEvents(): List<NotificationEventResponse> = notificationService.list()

    @GetMapping("/rules")
    fun listRules(): List<NotificationRuleResponse> = notificationService.listRules()

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRule(@RequestBody request: NotificationRuleCreateRequest): NotificationRuleResponse {
        require(request.keyword.isNotBlank()) { "keyword must not be blank" }
        require(request.channel.isNotBlank()) { "channel must not be blank" }
        return notificationService.createRule(request)
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
