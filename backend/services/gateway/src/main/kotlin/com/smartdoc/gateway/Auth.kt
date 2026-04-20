package com.smartdoc.gateway

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.multipart.MultipartException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val AUTH_USER_ATTRIBUTE = "smartdoc.auth.user"
const val SMARTDOC_USER_ID_HEADER = "X-SmartDoc-User-Id"
const val SMARTDOC_USER_EMAIL_HEADER = "X-SmartDoc-User-Email"
const val SMARTDOC_TRACE_ID_HEADER = "X-SmartDoc-Trace-Id"
const val TRACE_ID_ATTRIBUTE = "smartdoc.traceId"

private fun traceIdFrom(request: HttpServletRequest): String =
    (request.getAttribute(TRACE_ID_ATTRIBUTE) as? String)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: request.getHeader(SMARTDOC_TRACE_ID_HEADER)?.trim()?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString()

@Component
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

@Entity
@Table(name = "app_users")
class GatewayUserEntity(
    @Id
    @Column(name = "user_id", nullable = false)
    var userId: String = UUID.randomUUID().toString(),

    @Column(name = "email", nullable = false, unique = true)
    var email: String = "",

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",

    @Column(name = "display_name", nullable = false)
    var displayName: String = "",

    @Column(name = "role", nullable = false)
    var role: String = "USER",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

interface GatewayUserRepository : JpaRepository<GatewayUserEntity, String> {
    fun findByEmail(email: String): GatewayUserEntity?
    fun existsByEmail(email: String): Boolean
}

data class AuthSignupRequest(
    val email: String,
    val password: String,
    val displayName: String? = null
)

data class AuthLoginRequest(
    val email: String,
    val password: String
)

data class AuthUserResponse(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: String
)

data class AuthSessionResponse(
    val user: AuthUserResponse,
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresAt: Instant
)

data class AuthenticatedPrincipal(
    val userId: String,
    val email: String,
    val displayName: String,
    val role: String
)

@Service
class PasswordHashService {
    private val random = SecureRandom()
    private val iterations = 120_000
    private val keyLength = 256

    fun hash(rawPassword: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = pbkdf2(rawPassword, salt, iterations)
        return listOf(
            "pbkdf2-sha256",
            iterations.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        ).joinToString("\$")
    }

    fun matches(rawPassword: String, storedHash: String): Boolean {
        val parts = storedHash.split("\$")
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") {
            return false
        }

        val storedIterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrElse { return false }
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrElse { return false }
        val actual = pbkdf2(rawPassword, salt, storedIterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun pbkdf2(rawPassword: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(rawPassword.toCharArray(), salt, iterations, keyLength)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

@Service
class JwtTokenService(
    @Value("\${smartdoc.auth.jwt-secret:smartdoc-local-dev-secret-change-me}")
    private val jwtSecret: String,
    @Value("\${smartdoc.auth.access-token-ttl-seconds:86400}")
    private val ttlSeconds: Long,
    private val objectMapper: ObjectMapper
) {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun createToken(user: GatewayUserEntity): AuthSessionResponse {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(ttlSeconds)
        val header = mapOf("alg" to "HS256", "typ" to "JWT")
        val payload = mapOf(
            "sub" to user.userId,
            "email" to user.email,
            "displayName" to user.displayName,
            "role" to user.role,
            "iat" to now.epochSecond,
            "exp" to expiresAt.epochSecond
        )
        val unsignedToken = listOf(toBase64Json(header), toBase64Json(payload)).joinToString(".")
        val signature = encoder.encodeToString(hmac(unsignedToken))
        return AuthSessionResponse(user.toResponse(), "$unsignedToken.$signature", expiresAt = expiresAt)
    }

    fun verify(token: String): AuthenticatedPrincipal? {
        val parts = token.split(".")
        if (parts.size != 3) {
            return null
        }

        val unsignedToken = "${parts[0]}.${parts[1]}"
        val expectedSignature = encoder.encodeToString(hmac(unsignedToken))
        if (!MessageDigest.isEqual(expectedSignature.toByteArray(), parts[2].toByteArray())) {
            return null
        }

        val payloadJson = runCatching { String(decoder.decode(parts[1]), StandardCharsets.UTF_8) }.getOrElse { return null }
        val payload = runCatching {
            objectMapper.readValue(payloadJson, object : TypeReference<Map<String, Any>>() {})
        }.getOrElse { return null }
        val expiresAt = (payload["exp"] as? Number)?.toLong() ?: return null
        if (Instant.now().epochSecond >= expiresAt) {
            return null
        }

        return AuthenticatedPrincipal(
            userId = payload["sub"] as? String ?: return null,
            email = payload["email"] as? String ?: return null,
            displayName = payload["displayName"] as? String ?: "",
            role = payload["role"] as? String ?: "USER"
        )
    }

    private fun toBase64Json(value: Map<String, Any>): String =
        encoder.encodeToString(objectMapper.writeValueAsBytes(value))

    private fun hmac(value: String): ByteArray {
        val key = SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }
}

@Service
class AuthService(
    private val userRepository: GatewayUserRepository,
    private val passwordHashService: PasswordHashService,
    private val jwtTokenService: JwtTokenService
) {
    fun signup(request: AuthSignupRequest): AuthSessionResponse {
        val email = normalizeEmail(request.email)
        val password = request.password.trim()
        val displayName = request.displayName?.trim()?.takeIf { it.isNotBlank() } ?: email.substringBefore("@")

        require(email.contains("@")) { "email must be valid" }
        require(password.length >= 8) { "password must be at least 8 characters" }
        require(!userRepository.existsByEmail(email)) { "email already exists" }

        val user = userRepository.save(
            GatewayUserEntity(
                email = email,
                passwordHash = passwordHashService.hash(password),
                displayName = displayName
            )
        )
        return jwtTokenService.createToken(user)
    }

    fun login(request: AuthLoginRequest): AuthSessionResponse {
        val email = normalizeEmail(request.email)
        val user = userRepository.findByEmail(email) ?: throw UnauthorizedException("invalid email or password")
        if (!passwordHashService.matches(request.password.trim(), user.passwordHash)) {
            throw UnauthorizedException("invalid email or password")
        }
        return jwtTokenService.createToken(user)
    }

    fun findUser(userId: String): AuthUserResponse {
        val user = userRepository.findById(userId).orElseThrow { UnauthorizedException("user not found") }
        return user.toResponse()
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()
}

@Component
class GatewayAuthFilter(
    private val jwtTokenService: JwtTokenService,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {
    private val publicPaths = setOf(
        "/",
        "/api/v1/health",
        "/api/v1/auth/signup",
        "/api/v1/auth/login",
        "/api/v1/auth/logout"
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (isPublicPath(request.requestURI)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = request.getHeader("Authorization")
            ?.removePrefix("Bearer")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val principal = token?.let(jwtTokenService::verify)
        if (principal == null) {
            writeUnauthorized(response, request, if (token == null) "authentication required" else "invalid or expired token")
            return
        }

        request.setAttribute(AUTH_USER_ATTRIBUTE, principal)
        filterChain.doFilter(request, response)
    }

    private fun isPublicPath(path: String): Boolean =
        path in publicPaths ||
            path == "/swagger-ui.html" ||
            path.startsWith("/swagger-ui/") ||
            path.startsWith("/v3/api-docs") ||
            path == "/actuator/health" ||
            path.startsWith("/actuator/health/")

    private fun writeUnauthorized(response: HttpServletResponse, request: HttpServletRequest, message: String) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.writer.write(
            objectMapper.writeValueAsString(
                ApiErrorResponse(
                    timestamp = Instant.now(),
                    path = request.requestURI,
                    code = "AUTHENTICATION_REQUIRED",
                    message = message,
                    traceId = traceIdFrom(request)
                )
            )
        )
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/signup")
    fun signup(@RequestBody request: AuthSignupRequest): AuthSessionResponse = authService.signup(request)

    @PostMapping("/login")
    fun login(@RequestBody request: AuthLoginRequest): AuthSessionResponse = authService.login(request)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() {
        // JWT logout is client-side for local development. The client deletes the token.
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): AuthUserResponse {
        val principal = request.getAttribute(AUTH_USER_ATTRIBUTE) as? AuthenticatedPrincipal
            ?: throw UnauthorizedException("authentication required")
        return authService.findUser(principal.userId)
    }
}

@Configuration
class AuthSeedConfig {
    @Bean
    fun seedLocalUser(
        userRepository: GatewayUserRepository,
        passwordHashService: PasswordHashService,
        @Value("\${smartdoc.auth.dev-email:test@smartdoc.local}") devEmail: String,
        @Value("\${smartdoc.auth.dev-password:password}") devPassword: String,
        @Value("\${smartdoc.auth.dev-display-name:SmartDoc Tester}") devDisplayName: String
    ): CommandLineRunner = CommandLineRunner {
        val email = devEmail.trim().lowercase()
        if (email.isNotBlank() && !userRepository.existsByEmail(email)) {
            userRepository.save(
                GatewayUserEntity(
                    email = email,
                    passwordHash = passwordHashService.hash(devPassword),
                    displayName = devDisplayName.trim().ifBlank { "SmartDoc Tester" },
                    role = "USER"
                )
            )
        }
    }
}

@RestControllerAdvice
class GatewayExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidation(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "VALIDATION_ERROR",
                message = ex.message ?: "validation failed",
                traceId = traceIdFrom(request)
            )
        )

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "AUTHENTICATION_REQUIRED",
                message = ex.message ?: "authentication required",
                traceId = traceIdFrom(request)
            )
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class, MultipartException::class)
    fun handleMultipartError(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(
                timestamp = Instant.now(),
                path = request.requestURI,
                code = "VALIDATION_ERROR",
                message = "file size must be less than or equal to ${System.getenv("SMARTDOC_MAX_UPLOAD_BYTES") ?: "10485760"} bytes",
                traceId = traceIdFrom(request)
            )
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
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

class UnauthorizedException(message: String) : RuntimeException(message)

private fun GatewayUserEntity.toResponse(): AuthUserResponse = AuthUserResponse(
    userId = userId,
    email = email,
    displayName = displayName,
    role = role
)
