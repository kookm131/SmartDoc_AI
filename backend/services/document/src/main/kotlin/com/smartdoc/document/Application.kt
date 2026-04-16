package com.smartdoc.document

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.sync.RequestBody as AwsRequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

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
        "service" to "document",
        "status" to "ok"
    )
}

data class DocumentCreateRequest(
    val filename: String,
    val fileKey: String,
    val contentType: String? = null
)

data class DocumentCreateResponse(
    val documentId: String,
    val ownerUserId: String,
    val filename: String,
    val fileKey: String,
    val contentType: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant?
)

data class DocumentStatusUpdateRequest(
    val status: String
)

data class DocumentContentResponse(
    val documentId: String,
    val ownerUserId: String,
    val fileKey: String,
    val contentType: String?,
    val textContent: String?
)

data class ApiErrorResponse(
    val timestamp: Instant,
    val path: String,
    val code: String,
    val message: String,
    val traceId: String
)

data class ObjectStoreCommand(
    val filename: String,
    val fileKey: String,
    val contentType: String?
)

data class ObjectFileStoreCommand(
    val filename: String,
    val fileKey: String?,
    val contentType: String?,
    val bytes: ByteArray
)

data class ObjectStoreResult(
    val fileKey: String,
    val objectUrl: String
)

data class ObjectContentResult(
    val textContent: String?
)

interface ObjectStoragePort {
    fun store(command: ObjectStoreCommand): ObjectStoreResult
    fun storeFile(command: ObjectFileStoreCommand): ObjectStoreResult
    fun readText(fileKey: String, contentType: String?): ObjectContentResult
    fun resolveObjectUrl(fileKey: String): String
}

@Service
@Profile("local | mariadb")
class LocalObjectStorageAdapter(
    @Value("\${smartdoc.local-upload-dir:.smartdoc/uploads}")
    localUploadDir: String
) : ObjectStoragePort {
    private val uploadRoot: Path = Path.of(localUploadDir).toAbsolutePath().normalize()
    private val pdfMaxChars: Int = 20_000

    override fun store(command: ObjectStoreCommand): ObjectStoreResult {
        val normalizedKey = command.fileKey.trim().ifBlank {
            "uploads/${command.filename.trim()}"
        }
        return ObjectStoreResult(
            fileKey = normalizedKey,
            objectUrl = "local://$normalizedKey"
        )
    }

    override fun storeFile(command: ObjectFileStoreCommand): ObjectStoreResult {
        val normalizedKey = command.fileKey?.trim()?.ifBlank { null } ?: "uploads/${command.filename.trim()}"
        val storagePath = if (uploadRoot.fileName?.toString() == "uploads" && normalizedKey.startsWith("uploads/")) {
            normalizedKey.removePrefix("uploads/")
        } else {
            normalizedKey
        }
        val target = uploadRoot.resolve(storagePath).normalize()
        require(target.startsWith(uploadRoot)) { "fileKey must stay within local upload directory" }

        Files.createDirectories(target.parent)
        Files.write(target, command.bytes)

        return ObjectStoreResult(
            fileKey = normalizedKey,
            objectUrl = "local://$normalizedKey"
        )
    }

    override fun readText(fileKey: String, contentType: String?): ObjectContentResult {
        val target = resolveLocalPath(fileKey)
        if (!Files.exists(target)) {
            return ObjectContentResult(textContent = null)
        }

        if (contentType == "text/plain") {
            return ObjectContentResult(textContent = Files.readString(target))
        }

        if (contentType == "application/pdf") {
            return ObjectContentResult(textContent = extractPdfTextFromPath(target, pdfMaxChars))
        }

        return ObjectContentResult(textContent = null)
    }

    override fun resolveObjectUrl(fileKey: String): String = "local://${fileKey.trim()}"

    private fun resolveLocalPath(fileKey: String): Path {
        val normalizedKey = fileKey.trim()
        val storagePath = if (uploadRoot.fileName?.toString() == "uploads" && normalizedKey.startsWith("uploads/")) {
            normalizedKey.removePrefix("uploads/")
        } else {
            normalizedKey
        }
        val target = uploadRoot.resolve(storagePath).normalize()
        require(target.startsWith(uploadRoot)) { "fileKey must stay within local upload directory" }
        return target
    }

}

@Service
@Profile("aws")
class AwsObjectStorageAdapter(
    @Value("\${SMARTDOC_S3_BUCKET:smartdoc-local}")
    private val bucket: String,
    @Value("\${SMARTDOC_AWS_REGION:us-east-1}")
    private val region: String,
    @Value("\${SMARTDOC_AWS_S3_ENDPOINT:}")
    private val s3Endpoint: String,
    @Value("\${SMARTDOC_AWS_S3_PATH_STYLE:true}")
    private val pathStyleEnabled: Boolean,
    @Value("\${SMARTDOC_S3_AUTO_CREATE_BUCKET:false}")
    private val autoCreateBucket: Boolean,
    @Value("\${AWS_ACCESS_KEY_ID:}")
    private val accessKeyId: String,
    @Value("\${AWS_SECRET_ACCESS_KEY:}")
    private val secretAccessKey: String
) : ObjectStoragePort {
    private val pdfMaxChars: Int = 20_000
    private val bucketChecked = AtomicBoolean(false)

    private val s3: S3Client = run {
        val builder = S3Client.builder()
            .region(Region.of(region.trim().ifBlank { "us-east-1" }))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(pathStyleEnabled || s3Endpoint.isNotBlank())
                    .build()
            )

        val endpoint = s3Endpoint.trim()
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

    override fun store(command: ObjectStoreCommand): ObjectStoreResult {
        val normalizedKey = command.fileKey.trim().ifBlank {
            "uploads/${command.filename.trim()}"
        }
        return ObjectStoreResult(
            fileKey = normalizedKey,
            objectUrl = resolveObjectUrl(normalizedKey)
        )
    }

    override fun storeFile(command: ObjectFileStoreCommand): ObjectStoreResult {
        val normalizedKey = command.fileKey?.trim()?.ifBlank { null } ?: "uploads/${command.filename.trim()}"
        ensureBucketExistsIfNeeded()

        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName())
                .key(normalizedKey)
                .contentType(command.contentType?.trim()?.ifBlank { null })
                .build(),
            AwsRequestBody.fromBytes(command.bytes)
        )
        return ObjectStoreResult(
            fileKey = normalizedKey,
            objectUrl = resolveObjectUrl(normalizedKey)
        )
    }

    override fun readText(fileKey: String, contentType: String?): ObjectContentResult {
        val key = fileKey.trim()
        if (key.isBlank()) return ObjectContentResult(textContent = null)

        return try {
            s3.headObject(
                HeadObjectRequest.builder()
                    .bucket(bucketName())
                    .key(key)
                    .build()
            )

            val bytes: ResponseBytes<*> = s3.getObjectAsBytes(
                GetObjectRequest.builder()
                    .bucket(bucketName())
                    .key(key)
                    .build()
            )

            when (contentType?.trim()) {
                "text/plain" -> ObjectContentResult(textContent = bytes.asUtf8String())
                "application/pdf" -> ObjectContentResult(textContent = extractPdfTextFromBytes(bytes.asByteArray(), pdfMaxChars))
                else -> ObjectContentResult(textContent = null)
            }
        } catch (_: NoSuchKeyException) {
            ObjectContentResult(textContent = null)
        } catch (_: NoSuchBucketException) {
            ObjectContentResult(textContent = null)
        } catch (_: Exception) {
            ObjectContentResult(textContent = null)
        }
    }

    override fun resolveObjectUrl(fileKey: String): String =
        "s3://${bucketName()}/${fileKey.trim()}"

    private fun bucketName(): String = bucket.trim().ifBlank { "smartdoc-local" }

    private fun shouldAutoCreateBucket(): Boolean =
        autoCreateBucket || s3Endpoint.trim().isNotBlank()

    private fun ensureBucketExistsIfNeeded() {
        if (bucketChecked.get()) return
        synchronized(bucketChecked) {
            if (bucketChecked.get()) return

            val b = bucketName()
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(b).build())
            } catch (_: Exception) {
                if (shouldAutoCreateBucket()) {
                    try {
                        s3.createBucket(CreateBucketRequest.builder().bucket(b).build())
                    } catch (_: Exception) {
                        // ignore
                    }
                }
                Unit
            } finally {
                bucketChecked.set(true)
            }
        }
    }
}

class ResourceNotFoundException(message: String) : RuntimeException(message)

private fun extractPdfTextFromPath(path: Path, maxChars: Int): String? =
    try {
        Loader.loadPDF(path.toFile()).use { doc ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val raw = stripper.getText(doc)?.trim()
            when {
                raw.isNullOrBlank() -> null
                raw.length <= maxChars -> raw
                else -> raw.substring(0, maxChars)
            }
        }
    } catch (_: Exception) {
        null
    }

private fun extractPdfTextFromBytes(bytes: ByteArray, maxChars: Int): String? =
    try {
        Loader.loadPDF(bytes).use { doc ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val raw = stripper.getText(doc)?.trim()
            when {
                raw.isNullOrBlank() -> null
                raw.length <= maxChars -> raw
                else -> raw.substring(0, maxChars)
            }
        }
    } catch (_: Exception) {
        null
    }

private const val SMARTDOC_USER_ID_HEADER = "X-SmartDoc-User-Id"
private const val LOCAL_DEV_OWNER_USER_ID = "local-dev-user"

private fun ownerUserIdFrom(request: HttpServletRequest): String =
    request.getHeader(SMARTDOC_USER_ID_HEADER)?.trim()?.takeIf { it.isNotBlank() } ?: LOCAL_DEV_OWNER_USER_ID

@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "file_key", nullable = false, length = 512)
    var fileKey: String = "",

    @Column(name = "owner_user_id", nullable = false, length = 36)
    var ownerUserId: String = LOCAL_DEV_OWNER_USER_ID,

    @Column(nullable = false, length = 255)
    var filename: String = "",

    @Column(nullable = false, length = 32)
    var status: String = "RECEIVED",

    @Column(name = "content_type", length = 128)
    var contentType: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "archived_at")
    var archivedAt: Instant? = null
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }
}

interface DocumentRepository : JpaRepository<DocumentEntity, String> {
    fun findByIdAndOwnerUserId(id: String, ownerUserId: String): DocumentEntity?
    fun findByOwnerUserId(ownerUserId: String): List<DocumentEntity>
    fun findByOwnerUserIdAndStatusNot(ownerUserId: String, status: String): List<DocumentEntity>
}

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val objectStoragePort: ObjectStoragePort,
    @Value("\${smartdoc.max-upload-bytes:10485760}")
    private val maxUploadBytes: Long
) {
    fun create(request: DocumentCreateRequest, ownerUserId: String): DocumentCreateResponse {
        val stored = objectStoragePort.store(
            ObjectStoreCommand(
                filename = request.filename.trim(),
                fileKey = request.fileKey.trim(),
                contentType = request.contentType?.trim()
            )
        )

        val saved = documentRepository.save(
            DocumentEntity(
                fileKey = stored.fileKey,
                ownerUserId = ownerUserId,
                filename = request.filename.trim(),
                contentType = request.contentType?.trim(),
                status = "RECEIVED"
            )
        )

        return toResponse(saved)
    }

    fun upload(file: MultipartFile, fileKey: String?, ownerUserId: String): DocumentCreateResponse {
        val filename = Path.of(file.originalFilename ?: "").fileName.toString().trim()
        val contentType = file.contentType?.trim()?.ifBlank { null } ?: "application/octet-stream"

        require(filename.isNotBlank()) { "filename must not be blank" }
        require(!file.isEmpty) { "file must not be empty" }
        require(file.size <= maxUploadBytes) {
            "file size must be less than or equal to $maxUploadBytes bytes"
        }
        validateUploadType(filename, contentType)

        val stored = objectStoragePort.storeFile(
            ObjectFileStoreCommand(
                filename = filename,
                fileKey = fileKey,
                contentType = contentType,
                bytes = file.bytes
            )
        )

        val saved = documentRepository.save(
            DocumentEntity(
                fileKey = stored.fileKey,
                ownerUserId = ownerUserId,
                filename = filename,
                contentType = contentType,
                status = "RECEIVED"
            )
        )

        return toResponse(saved)
    }

    fun getById(documentId: String, ownerUserId: String): DocumentCreateResponse {
        val found = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
            ?: throw ResourceNotFoundException("document not found: $documentId")
        return toResponse(found)
    }

    fun getContent(documentId: String, ownerUserId: String): DocumentContentResponse {
        val found = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
            ?: throw ResourceNotFoundException("document not found: $documentId")
        val content = objectStoragePort.readText(found.fileKey, found.contentType)
        return DocumentContentResponse(
            documentId = found.id,
            ownerUserId = found.ownerUserId,
            fileKey = found.fileKey,
            contentType = found.contentType,
            textContent = content.textContent
        )
    }

    fun list(ownerUserId: String): List<DocumentCreateResponse> =
        documentRepository.findByOwnerUserIdAndStatusNot(ownerUserId, ARCHIVED_STATUS)
            .sortedByDescending { it.createdAt }
            .map(::toResponse)

    fun archive(documentId: String, ownerUserId: String): DocumentCreateResponse {
        val found = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
            ?: throw ResourceNotFoundException("document not found: $documentId")

        if (found.status == ARCHIVED_STATUS) {
            return toResponse(found)
        }

        found.status = ARCHIVED_STATUS
        found.archivedAt = Instant.now()
        return toResponse(documentRepository.save(found))
    }

    fun listArchived(ownerUserId: String): List<DocumentCreateResponse> = documentRepository.findByOwnerUserId(ownerUserId)
        .filter { it.status == ARCHIVED_STATUS }
        .sortedByDescending { it.createdAt }
        .map(::toResponse)

    fun updateStatus(documentId: String, request: DocumentStatusUpdateRequest, ownerUserId: String): DocumentCreateResponse {
        val normalizedStatus = request.status.trim().uppercase()
        require(normalizedStatus in ALLOWED_STATUSES) { "unsupported document status: $normalizedStatus" }

        val found = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
            ?: throw ResourceNotFoundException("document not found: $documentId")

        if (found.status == ARCHIVED_STATUS) {
            return toResponse(found)
        }

        found.status = normalizedStatus
        return toResponse(documentRepository.save(found))
    }

    private fun toResponse(entity: DocumentEntity): DocumentCreateResponse =
        DocumentCreateResponse(
            documentId = entity.id,
            ownerUserId = entity.ownerUserId,
            filename = entity.filename,
            fileKey = entity.fileKey,
            contentType = entity.contentType,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            archivedAt = entity.archivedAt
        )

    private fun validateUploadType(filename: String, contentType: String) {
        val extension = filename.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .trim()
        require(extension.isNotBlank()) { "file extension is required" }

        val allowedContentTypes = ALLOWED_UPLOAD_TYPES[extension]
            ?: throw IllegalArgumentException("unsupported file extension: .$extension")

        require(contentType in allowedContentTypes) {
            "unsupported contentType $contentType for .$extension file"
        }
    }

    companion object {
        private const val ARCHIVED_STATUS = "ARCHIVED"

        private val ALLOWED_STATUSES = setOf(
            "RECEIVED",
            "ANALYSIS_QUEUED",
            "ANALYSIS_PROCESSING",
            "ANALYSIS_COMPLETED",
            "ANALYSIS_FAILED",
            ARCHIVED_STATUS
        )

        private val ALLOWED_UPLOAD_TYPES = mapOf(
            "pdf" to setOf("application/pdf"),
            "txt" to setOf("text/plain"),
            "bin" to setOf("application/octet-stream")
        )
    }
}

@RestController
@RequestMapping("/api/v1/documents")
class DocumentController(
    private val documentService: DocumentService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createDocument(
        @RequestBody request: DocumentCreateRequest,
        servletRequest: HttpServletRequest
    ): DocumentCreateResponse {
        require(request.filename.isNotBlank()) { "filename must not be blank" }
        require(request.fileKey.isNotBlank()) { "fileKey must not be blank" }
        return documentService.create(request, ownerUserIdFrom(servletRequest))
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDocument(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("fileKey", required = false) fileKey: String?,
        servletRequest: HttpServletRequest
    ): DocumentCreateResponse = documentService.upload(file, fileKey, ownerUserIdFrom(servletRequest))

    @GetMapping("/archived")
    fun listArchivedDocuments(servletRequest: HttpServletRequest): List<DocumentCreateResponse> =
        documentService.listArchived(ownerUserIdFrom(servletRequest))

    @GetMapping
    fun listDocuments(servletRequest: HttpServletRequest): List<DocumentCreateResponse> =
        documentService.list(ownerUserIdFrom(servletRequest))

    @GetMapping("/{id}")
    fun getDocument(@PathVariable id: String, servletRequest: HttpServletRequest): DocumentCreateResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return documentService.getById(id, ownerUserIdFrom(servletRequest))
    }

    @GetMapping("/{id}/content")
    fun getDocumentContent(@PathVariable id: String, servletRequest: HttpServletRequest): DocumentContentResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return documentService.getContent(id, ownerUserIdFrom(servletRequest))
    }

    @PostMapping("/{id}/archive")
    fun archiveDocument(@PathVariable id: String, servletRequest: HttpServletRequest): DocumentCreateResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return documentService.archive(id, ownerUserIdFrom(servletRequest))
    }

    @PatchMapping("/{id}/status")
    fun updateDocumentStatus(
        @PathVariable id: String,
        @RequestBody request: DocumentStatusUpdateRequest,
        servletRequest: HttpServletRequest
    ): DocumentCreateResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        require(request.status.isNotBlank()) { "status must not be blank" }
        return documentService.updateStatus(id, request, ownerUserIdFrom(servletRequest))
    }

    @PostMapping("/{id}/status")
    fun postDocumentStatus(
        @PathVariable id: String,
        @RequestBody request: DocumentStatusUpdateRequest,
        servletRequest: HttpServletRequest
    ): DocumentCreateResponse = updateDocumentStatus(id, request, servletRequest)
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

    @ExceptionHandler(MaxUploadSizeExceededException::class, MultipartException::class)
    fun handleMultipartError(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> =
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
