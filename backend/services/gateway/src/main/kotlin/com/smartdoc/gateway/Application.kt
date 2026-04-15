package com.smartdoc.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ByteArrayResource
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.util.StreamUtils
import org.springframework.util.MultiValueMap
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@RestController
class RootController {
    @GetMapping("/")
    fun root(): Map<String, String> = mapOf(
        "service" to "gateway",
        "message" to "gateway up"
    )
}

@RestController
@RequestMapping("/api/v1")
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf(
        "service" to "gateway",
        "status" to "ok"
    )
}

data class ApiErrorResponse(
    val timestamp: Instant,
    val path: String,
    val code: String,
    val message: String,
    val traceId: String
)

@Service
class GatewayProxy(
    @Value("\${smartdoc.document.base-url:http://localhost:8081}")
    documentBaseUrl: String,
    @Value("\${smartdoc.analysis.base-url:http://localhost:8082}")
    analysisBaseUrl: String,
    @Value("\${smartdoc.notification.base-url:http://localhost:8083}")
    notificationBaseUrl: String,
    @Value("\${smartdoc.upstream.connect-timeout-ms:1000}")
    private val connectTimeoutMs: Int,
    @Value("\${smartdoc.upstream.read-timeout-ms:3000}")
    private val readTimeoutMs: Int,
    private val objectMapper: ObjectMapper
) {
    private val documentClient = restClient(documentBaseUrl)
    private val analysisClient = restClient(analysisBaseUrl)
    private val notificationClient = restClient(notificationBaseUrl)

    fun getDocument(path: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy(HttpMethod.GET, UpstreamTarget.DOCUMENT, path, null, request)

    fun postDocument(path: String, body: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy(HttpMethod.POST, UpstreamTarget.DOCUMENT, path, body, request)

    fun postDocumentUpload(
        path: String,
        file: MultipartFile,
        fileKey: String?,
        request: HttpServletRequest
    ): ResponseEntity<String> =
        proxyMultipart(UpstreamTarget.DOCUMENT, path, file, fileKey, request)

    fun patchDocument(path: String, body: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy(HttpMethod.PATCH, UpstreamTarget.DOCUMENT, path, body, request)

    fun postAnalysis(path: String, body: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy(HttpMethod.POST, UpstreamTarget.ANALYSIS, path, body, request)

    fun getAnalysis(path: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy(HttpMethod.GET, UpstreamTarget.ANALYSIS, path, null, request)

    fun postNotification(path: String, body: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy(HttpMethod.POST, UpstreamTarget.NOTIFICATION, path, body, request)

    fun getNotification(path: String, request: HttpServletRequest): ResponseEntity<String> =
        proxy(HttpMethod.GET, UpstreamTarget.NOTIFICATION, path, null, request)

    private fun restClient(baseUrl: String): RestClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(connectTimeoutMs)
                    setReadTimeout(readTimeoutMs)
                }
            )
            .build()

    private fun proxy(
        method: HttpMethod,
        target: UpstreamTarget,
        path: String,
        body: String?,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val client = clientFor(target)
        val requestSpec = client.method(method)
            .uri(path)
            .accept(MediaType.APPLICATION_JSON)
            .withUserHeaders(request)

        if (body != null) {
            requestSpec.contentType(MediaType.APPLICATION_JSON).body(body)
        }

        return try {
            requestSpec.exchange { _, response ->
                val responseBody = StreamUtils.copyToString(response.body, StandardCharsets.UTF_8)
                ResponseEntity.status(response.statusCode)
                    .contentType(response.headers.contentType ?: MediaType.APPLICATION_JSON)
                    .body(responseBody)
            } ?: upstreamError(target, request, "empty upstream response")
        } catch (ex: RestClientException) {
            upstreamError(target, request, "upstream request failed")
        }
    }

    private fun proxyMultipart(
        target: UpstreamTarget,
        path: String,
        file: MultipartFile,
        fileKey: String?,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        val resource = object : ByteArrayResource(file.bytes) {
            override fun getFilename(): String = file.originalFilename ?: "upload.bin"
        }

        body.add("file", resource)
        fileKey?.trim()?.takeIf { it.isNotBlank() }?.let { body.add("fileKey", it) }

        return try {
            clientFor(target).post()
                .uri(path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .withUserHeaders(request)
                .body(body)
                .exchange { _, response ->
                    val responseBody = StreamUtils.copyToString(response.body, StandardCharsets.UTF_8)
                    ResponseEntity.status(response.statusCode)
                        .contentType(response.headers.contentType ?: MediaType.APPLICATION_JSON)
                        .body(responseBody)
                } ?: upstreamError(target, request, "empty upstream response")
        } catch (ex: RestClientException) {
            upstreamError(target, request, "upstream request failed")
        }
    }

    private fun clientFor(target: UpstreamTarget): RestClient =
        when (target) {
            UpstreamTarget.DOCUMENT -> documentClient
            UpstreamTarget.ANALYSIS -> analysisClient
            UpstreamTarget.NOTIFICATION -> notificationClient
        }

    private fun RestClient.RequestBodySpec.withUserHeaders(request: HttpServletRequest): RestClient.RequestBodySpec {
        val principal = request.getAttribute(AUTH_USER_ATTRIBUTE) as? AuthenticatedPrincipal
        if (principal != null) {
            header(SMARTDOC_USER_ID_HEADER, principal.userId)
            header(SMARTDOC_USER_EMAIL_HEADER, principal.email)
        }
        return this
    }

    private fun upstreamError(
        target: UpstreamTarget,
        request: HttpServletRequest,
        message: String
    ): ResponseEntity<String> {
        val response = ApiErrorResponse(
            timestamp = Instant.now(),
            path = request.requestURI,
            code = "UPSTREAM_${target.name}_ERROR",
            message = message,
            traceId = UUID.randomUUID().toString()
        )
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .contentType(MediaType.APPLICATION_JSON)
            .body(objectMapper.writeValueAsString(response))
    }

    private enum class UpstreamTarget {
        DOCUMENT,
        ANALYSIS,
        NOTIFICATION
    }
}

@RestController
@RequestMapping("/api/v1/documents")
class DocumentProxyController(
    private val gatewayProxy: GatewayProxy
) {
    @GetMapping
    fun listDocuments(request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getDocument("/api/v1/documents", request)

    @GetMapping("/archived")
    fun listArchivedDocuments(request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getDocument("/api/v1/documents/archived", request)

    @GetMapping("/{id}")
    fun getDocument(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getDocument("/api/v1/documents/$id", request)

    @GetMapping("/{id}/content")
    fun getDocumentContent(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getDocument("/api/v1/documents/$id/content", request)

    @PostMapping
    fun createDocument(@RequestBody body: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.postDocument("/api/v1/documents", body, request)

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadDocument(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("fileKey", required = false) fileKey: String?,
        request: HttpServletRequest
    ): ResponseEntity<String> =
        gatewayProxy.postDocumentUpload("/api/v1/documents/upload", file, fileKey, request)

    @PatchMapping("/{id}/status")
    fun patchDocumentStatus(
        @PathVariable id: String,
        @RequestBody body: String,
        request: HttpServletRequest
    ): ResponseEntity<String> =
        gatewayProxy.patchDocument("/api/v1/documents/$id/status", body, request)

    @PostMapping("/{id}/status")
    fun postDocumentStatus(
        @PathVariable id: String,
        @RequestBody body: String,
        request: HttpServletRequest
    ): ResponseEntity<String> =
        gatewayProxy.postDocument("/api/v1/documents/$id/status", body, request)

    @PostMapping("/{id}/archive")
    fun archiveDocument(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.postDocument("/api/v1/documents/$id/archive", "{}", request)
}

@RestController
@RequestMapping("/api/v1/analysis/jobs")
class AnalysisProxyController(
    private val gatewayProxy: GatewayProxy
) {
    @PostMapping
    fun createJob(@RequestBody body: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.postAnalysis("/api/v1/analysis/jobs", body, request)

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getAnalysis("/api/v1/analysis/jobs/$id", request)

    @PostMapping("/{id}/retry")
    fun retryJob(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.postAnalysis("/api/v1/analysis/jobs/$id/retry", "{}", request)
}

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationProxyController(
    private val gatewayProxy: GatewayProxy
) {
    @PostMapping("/dispatch")
    fun dispatch(@RequestBody body: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.postNotification("/api/v1/notifications/dispatch", body, request)

    @GetMapping("/events")
    fun listEvents(request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getNotification("/api/v1/notifications/events", request)

    @GetMapping("/events/{id}")
    fun getEvent(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getNotification("/api/v1/notifications/events/$id", request)

    @GetMapping("/rules")
    fun listRules(request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.getNotification("/api/v1/notifications/rules", request)

    @PostMapping("/rules")
    fun createRule(@RequestBody body: String, request: HttpServletRequest): ResponseEntity<String> =
        gatewayProxy.postNotification("/api/v1/notifications/rules", body, request)
}
