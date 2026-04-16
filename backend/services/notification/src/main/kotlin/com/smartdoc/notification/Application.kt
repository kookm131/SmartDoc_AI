package com.smartdoc.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant
import java.util.UUID

@SpringBootApplication
class Application {
    @Bean
    fun seedNotificationRules(notificationRuleRepository: NotificationRuleRepository) = CommandLineRunner {
        if (!notificationRuleRepository.existsByOwnerUserIdAndKeywordAndChannel(LOCAL_DEV_OWNER_USER_ID, "계약", "slack")) {
            notificationRuleRepository.save(
                NotificationRuleEntity(
                    ownerUserId = LOCAL_DEV_OWNER_USER_ID,
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

private const val SMARTDOC_TRACE_ID_HEADER = "X-SmartDoc-Trace-Id"
private const val TRACE_ID_ATTRIBUTE = "smartdoc.traceId"

private fun traceIdFrom(request: HttpServletRequest): String =
    (request.getAttribute(TRACE_ID_ATTRIBUTE) as? String)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: request.getHeader(SMARTDOC_TRACE_ID_HEADER)?.trim()?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString()

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
    val ownerUserId: String,
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

data class NotificationRuleUpdateRequest(
    val keyword: String? = null,
    val channel: String? = null,
    val enabled: Boolean? = null
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

private const val SMARTDOC_USER_ID_HEADER = "X-SmartDoc-User-Id"
private const val LOCAL_DEV_OWNER_USER_ID = "local-dev-user"

private fun ownerUserIdFrom(request: HttpServletRequest): String =
    request.getHeader(SMARTDOC_USER_ID_HEADER)?.trim()?.takeIf { it.isNotBlank() } ?: LOCAL_DEV_OWNER_USER_ID

@Entity
@Table(name = "notification_events")
class NotificationEventEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "document_id", nullable = false, length = 36)
    var documentId: String = "",

    @Column(name = "owner_user_id", nullable = false, length = 36)
    var ownerUserId: String = LOCAL_DEV_OWNER_USER_ID,

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

interface NotificationEventRepository : JpaRepository<NotificationEventEntity, String> {
    fun findByIdAndOwnerUserId(id: String, ownerUserId: String): NotificationEventEntity?
    fun findByOwnerUserId(ownerUserId: String): List<NotificationEventEntity>
}

@Entity
@Table(
    name = "notification_rules",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_rules_keyword_channel",
            columnNames = ["owner_user_id", "keyword", "channel"]
        )
    ]
)
class NotificationRuleEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 128)
    var keyword: String = "",

    @Column(name = "owner_user_id", nullable = false, length = 36)
    var ownerUserId: String = LOCAL_DEV_OWNER_USER_ID,

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
    fun existsByOwnerUserIdAndKeywordAndChannel(ownerUserId: String, keyword: String, channel: String): Boolean
    fun findByOwnerUserIdAndKeywordAndChannel(ownerUserId: String, keyword: String, channel: String): NotificationRuleEntity?
    fun findByOwnerUserIdAndEnabledTrue(ownerUserId: String): List<NotificationRuleEntity>
    fun findByOwnerUserId(ownerUserId: String): List<NotificationRuleEntity>
    fun findByIdAndOwnerUserId(id: String, ownerUserId: String): NotificationRuleEntity?
}

@Service
class NotificationService(
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationRuleRepository: NotificationRuleRepository
) {
    fun create(request: NotificationDispatchRequest, ownerUserId: String): NotificationEventResponse? {
        if (request.keywords.isNotEmpty()) {
            // Ensure default rules exist for this user
            ensureDefaultRulesExist(ownerUserId)

            val matchingRule = notificationRuleRepository.findByOwnerUserIdAndEnabledTrue(ownerUserId)
                .firstOrNull { rule -> request.keywords.any { it == rule.keyword } }
                ?: return null

            return saveEvent(
                documentId = request.documentId.trim(),
                ownerUserId = ownerUserId,
                channel = matchingRule.channel,
                message = analysisCompletedMessage(
                    keyword = matchingRule.keyword,
                    riskScore = request.riskScore
                )
            )
        }

        return saveEvent(
            documentId = request.documentId.trim(),
            ownerUserId = ownerUserId,
            channel = request.channel?.trim()?.lowercase() ?: "",
            message = request.message?.trim() ?: ""
        )
    }

    fun createRule(request: NotificationRuleCreateRequest, ownerUserId: String): NotificationRuleResponse {
        val keyword = request.keyword.trim()
        val channel = request.channel.trim().lowercase()
        val rule = notificationRuleRepository.findByOwnerUserIdAndKeywordAndChannel(ownerUserId, keyword, channel)
            ?.also { it.enabled = request.enabled }
            ?: NotificationRuleEntity(
                ownerUserId = ownerUserId,
                keyword = keyword,
                channel = channel,
                enabled = request.enabled
            )
        val saved = notificationRuleRepository.save(rule)
        return toRuleResponse(saved)
    }

    fun listRules(ownerUserId: String): List<NotificationRuleResponse> =
        notificationRuleRepository.findByOwnerUserId(ownerUserId)
            .sortedWith(compareBy<NotificationRuleEntity> { it.keyword }.thenBy { it.channel })
            .map(::toRuleResponse)

    fun updateRule(ruleId: String, request: NotificationRuleUpdateRequest, ownerUserId: String): NotificationRuleResponse {
        val found = notificationRuleRepository.findByIdAndOwnerUserId(ruleId, ownerUserId)
            ?: throw ResourceNotFoundException("notification rule not found: $ruleId")

        val nextKeyword = request.keyword
            ?.trim()
            ?.also { require(it.isNotBlank()) { "keyword must not be blank" } }
            ?: found.keyword

        val nextChannel = request.channel
            ?.trim()
            ?.also { require(it.isNotBlank()) { "channel must not be blank" } }
            ?.lowercase()
            ?: found.channel

        val nextEnabled = request.enabled ?: found.enabled

        // Prevent violating (owner, keyword, channel) uniqueness when updating.
        notificationRuleRepository.findByOwnerUserIdAndKeywordAndChannel(ownerUserId, nextKeyword, nextChannel)
            ?.takeIf { it.id != found.id }
            ?.let {
                throw IllegalArgumentException("notification rule already exists for keyword=$nextKeyword channel=$nextChannel")
            }

        found.keyword = nextKeyword
        found.channel = nextChannel
        found.enabled = nextEnabled

        return toRuleResponse(notificationRuleRepository.save(found))
    }

    fun deleteRule(ruleId: String, ownerUserId: String) {
        val found = notificationRuleRepository.findByIdAndOwnerUserId(ruleId, ownerUserId)
            ?: throw ResourceNotFoundException("notification rule not found: $ruleId")
        notificationRuleRepository.delete(found)
    }

    private fun ensureDefaultRulesExist(ownerUserId: String) {
        if (!notificationRuleRepository.existsByOwnerUserIdAndKeywordAndChannel(ownerUserId, "계약", "slack")) {
            notificationRuleRepository.save(
                NotificationRuleEntity(
                    ownerUserId = ownerUserId,
                    keyword = "계약",
                    channel = "slack",
                    enabled = true
                )
            )
        }
    }

    private fun saveEvent(
        documentId: String,
        ownerUserId: String,
        channel: String,
        message: String
    ): NotificationEventResponse {
        val saved = notificationEventRepository.save(
            NotificationEventEntity(
                documentId = documentId,
                ownerUserId = ownerUserId,
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

    fun get(eventId: String, ownerUserId: String): NotificationEventResponse =
        notificationEventRepository.findByIdAndOwnerUserId(eventId, ownerUserId)
            ?.let(::toResponse)
            ?: throw ResourceNotFoundException("notification event not found: $eventId")

    fun list(ownerUserId: String): List<NotificationEventResponse> =
        notificationEventRepository.findByOwnerUserId(ownerUserId)
            .sortedByDescending { it.createdAt }
            .map(::toResponse)

    private fun toResponse(entity: NotificationEventEntity): NotificationEventResponse =
        NotificationEventResponse(
            eventId = entity.id,
            ownerUserId = entity.ownerUserId,
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
    fun dispatch(
        @RequestBody request: NotificationDispatchRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<NotificationEventResponse> {
        require(request.documentId.isNotBlank()) { "documentId must not be blank" }

        if (request.keywords.isEmpty()) {
            require(!request.channel.isNullOrBlank()) { "channel must not be blank" }
            require(!request.message.isNullOrBlank()) { "message must not be blank" }
        }

        val event = notificationService.create(request, ownerUserIdFrom(servletRequest))
            ?: return ResponseEntity.noContent().build()

        return ResponseEntity.status(HttpStatus.CREATED).body(event)
    }

    @GetMapping("/events/{id}")
    fun getEvent(@PathVariable id: String, servletRequest: HttpServletRequest): NotificationEventResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return notificationService.get(id, ownerUserIdFrom(servletRequest))
    }

    @GetMapping("/events")
    fun listEvents(servletRequest: HttpServletRequest): List<NotificationEventResponse> =
        notificationService.list(ownerUserIdFrom(servletRequest))

    @GetMapping("/rules")
    fun listRules(servletRequest: HttpServletRequest): List<NotificationRuleResponse> =
        notificationService.listRules(ownerUserIdFrom(servletRequest))

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRule(
        @RequestBody request: NotificationRuleCreateRequest,
        servletRequest: HttpServletRequest
    ): NotificationRuleResponse {
        require(request.keyword.isNotBlank()) { "keyword must not be blank" }
        require(request.channel.isNotBlank()) { "channel must not be blank" }
        return notificationService.createRule(request, ownerUserIdFrom(servletRequest))
    }

    @PatchMapping("/rules/{id}")
    fun updateRule(
        @PathVariable id: String,
        @RequestBody request: NotificationRuleUpdateRequest,
        servletRequest: HttpServletRequest
    ): NotificationRuleResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        require(request.keyword != null || request.channel != null || request.enabled != null) {
            "at least one field must be provided"
        }
        return notificationService.updateRule(id, request, ownerUserIdFrom(servletRequest))
    }

    @DeleteMapping("/rules/{id}")
    fun deleteRule(@PathVariable id: String, servletRequest: HttpServletRequest): ResponseEntity<Void> {
        require(id.isNotBlank()) { "id must not be blank" }
        notificationService.deleteRule(id, ownerUserIdFrom(servletRequest))
        return ResponseEntity.noContent().build()
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
                message = "unexpected server error",
                traceId = traceIdFrom(request)
            )
        )
}
