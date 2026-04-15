package com.smartdoc.gateway

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.blankOrNullString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

@SpringBootTest
@AutoConfigureMockMvc
class GatewayProxyApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @AfterEach
    fun tearDown() {
        upstreamServer.clear()
    }

    @Test
    fun `routes document create through gateway`() {
        upstreamServer.respond(
            "POST",
            "/api/v1/documents",
            201,
            """{"documentId":"doc-1","status":"RECEIVED"}"""
        )

        mockMvc.perform(
            post("/api/v1/documents")
                .header("Authorization", bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"gateway.pdf","fileKey":"uploads/gateway.pdf"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.documentId").value("doc-1"))

        upstreamServer.assertLastRequest("POST", "/api/v1/documents")
        upstreamServer.assertLastHeaderPresent("X-SmartDoc-User-Id")
        upstreamServer.assertLastHeader("X-SmartDoc-User-Email", "test@smartdoc.local")
    }

    @Test
    fun `routes document upload through gateway`() {
        upstreamServer.respond(
            "POST",
            "/api/v1/documents/upload",
            201,
            """{"documentId":"doc-1","filename":"gateway.pdf","fileKey":"uploads/gateway.pdf","status":"RECEIVED"}"""
        )
        val file = MockMultipartFile(
            "file",
            "gateway.pdf",
            "application/pdf",
            "hello gateway".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/v1/documents/upload")
                .file(file)
                .param("fileKey", "uploads/gateway.pdf")
                .header("Authorization", bearerToken())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.documentId").value("doc-1"))

        upstreamServer.assertLastRequest("POST", "/api/v1/documents/upload")
    }

    @Test
    fun `routes document content through gateway`() {
        upstreamServer.respond(
            "GET",
            "/api/v1/documents/doc-1/content",
            200,
            """{"documentId":"doc-1","textContent":"긴급 계약 검토"}"""
        )

        mockMvc.perform(
            get("/api/v1/documents/doc-1/content")
                .header("Authorization", bearerToken())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.textContent").value("긴급 계약 검토"))

        upstreamServer.assertLastRequest("GET", "/api/v1/documents/doc-1/content")
    }

    @Test
    fun `routes analysis get through gateway`() {
        upstreamServer.respond(
            "GET",
            "/api/v1/analysis/jobs/job-1",
            200,
            """{"jobId":"job-1","state":"COMPLETED"}"""
        )

        mockMvc.perform(
            get("/api/v1/analysis/jobs/job-1")
                .header("Authorization", bearerToken())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("COMPLETED"))

        upstreamServer.assertLastRequest("GET", "/api/v1/analysis/jobs/job-1")
    }

    @Test
    fun `routes notification rule create through gateway`() {
        upstreamServer.respond(
            "POST",
            "/api/v1/notifications/rules",
            201,
            """{"ruleId":"rule-1","keyword":"계약","channel":"slack","enabled":true}"""
        )

        mockMvc.perform(
            post("/api/v1/notifications/rules")
                .header("Authorization", bearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keyword":"계약","channel":"slack","enabled":true}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ruleId").value("rule-1"))

        upstreamServer.assertLastRequest("POST", "/api/v1/notifications/rules")
    }

    @Test
    fun `routes notification rule list through gateway`() {
        upstreamServer.respond(
            "GET",
            "/api/v1/notifications/rules",
            200,
            """[{"ruleId":"rule-1","keyword":"계약","channel":"slack","enabled":true}]"""
        )

        mockMvc.perform(
            get("/api/v1/notifications/rules")
                .header("Authorization", bearerToken())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].keyword").value("계약"))

        upstreamServer.assertLastRequest("GET", "/api/v1/notifications/rules")
    }

    @Test
    fun `returns gateway upstream error when upstream is unavailable`() {
        upstreamServer.stop()

        mockMvc.perform(
            get("/api/v1/documents")
                .header("Authorization", bearerToken())
        )
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("UPSTREAM_DOCUMENT_ERROR"))
            .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
    }

    @Test
    fun `logs in with seeded local user and returns current user`() {
        val token = bearerToken()

        mockMvc.perform(
            get("/api/v1/auth/me")
                .header("Authorization", token)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("test@smartdoc.local"))
            .andExpect(jsonPath("$.displayName").value("SmartDoc Tester"))
    }

    @Test
    fun `rejects protected gateway route without token`() {
        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
    }

    private fun bearerToken(): String {
        val response = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@smartdoc.local","password":"password"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val accessToken = objectMapper.readTree(response).get("accessToken").asText()
        return "Bearer $accessToken"
    }

    companion object {
        private val upstreamServer = StubUpstreamServer()

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            upstreamServer.start()
            registry.add("smartdoc.document.base-url") { upstreamServer.baseUrl() }
            registry.add("smartdoc.analysis.base-url") { upstreamServer.baseUrl() }
            registry.add("smartdoc.notification.base-url") { upstreamServer.baseUrl() }
            registry.add("smartdoc.upstream.connect-timeout-ms") { "500" }
            registry.add("smartdoc.upstream.read-timeout-ms") { "500" }
        }
    }
}

class StubUpstreamServer {
    private var server: HttpServer? = null
    private var port: Int? = null
    private var responseStatus: Int = 200
    private var responseBody: String = "{}"
    private var expectedMethod: String = "GET"
    private var expectedPath: String = "/"
    private var lastMethod: String? = null
    private var lastPath: String? = null
    private var lastHeaders: Map<String, List<String>> = emptyMap()

    fun start() {
        if (server != null) {
            return
        }

        server = HttpServer.create(InetSocketAddress(port ?: 0), 0).also { httpServer ->
            port = httpServer.address.port
            httpServer.createContext("/") { exchange -> handle(exchange) }
            httpServer.start()
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    fun clear() {
        responseStatus = 200
        responseBody = "{}"
        expectedMethod = "GET"
        expectedPath = "/"
        lastMethod = null
        lastPath = null
        lastHeaders = emptyMap()
        start()
    }

    fun baseUrl(): String = "http://localhost:${port ?: error("server not started")}"

    fun respond(method: String, path: String, status: Int, body: String) {
        expectedMethod = method
        expectedPath = path
        responseStatus = status
        responseBody = body
    }

    fun assertLastRequest(method: String, path: String) {
        check(lastMethod == method) { "expected method $method, got $lastMethod" }
        check(lastPath == path) { "expected path $path, got $lastPath" }
    }

    fun assertLastHeader(name: String, value: String) {
        val actual = lastHeaders.firstNotNullOfOrNull { (key, values) ->
            values.firstOrNull()?.takeIf { key.equals(name, ignoreCase = true) }
        }
        check(actual == value) { "expected header $name=$value, got $actual" }
    }

    fun assertLastHeaderPresent(name: String) {
        val actual = lastHeaders.firstNotNullOfOrNull { (key, values) ->
            values.firstOrNull()?.takeIf { key.equals(name, ignoreCase = true) }
        }
        check(!actual.isNullOrBlank()) { "expected header $name to be present" }
    }

    private fun handle(exchange: HttpExchange) {
        lastMethod = exchange.requestMethod
        lastPath = exchange.requestURI.path
        lastHeaders = exchange.requestHeaders.mapKeys { it.key }

        val status = if (lastMethod == expectedMethod && lastPath == expectedPath) {
            responseStatus
        } else {
            404
        }
        val body = if (status == responseStatus) responseBody else """{"code":"NOT_FOUND"}"""
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
