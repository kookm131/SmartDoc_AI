package com.smartdoc.document

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
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
    val filename: String,
    val fileKey: String,
    val contentType: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class DocumentStatusUpdateRequest(
    val status: String
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

data class ObjectStoreResult(
    val fileKey: String,
    val objectUrl: String
)

interface ObjectStoragePort {
    fun store(command: ObjectStoreCommand): ObjectStoreResult
    fun resolveObjectUrl(fileKey: String): String
}

@Service
@Profile("local")
class LocalObjectStorageAdapter : ObjectStoragePort {
    override fun store(command: ObjectStoreCommand): ObjectStoreResult {
        val normalizedKey = command.fileKey.trim().ifBlank {
            "uploads/${command.filename.trim()}"
        }
        return ObjectStoreResult(
            fileKey = normalizedKey,
            objectUrl = "local://$normalizedKey"
        )
    }

    override fun resolveObjectUrl(fileKey: String): String = "local://${fileKey.trim()}"
}

@Service
@Profile("aws")
class AwsObjectStorageAdapter : ObjectStoragePort {
    override fun store(command: ObjectStoreCommand): ObjectStoreResult {
        val normalizedKey = command.fileKey.trim().ifBlank {
            "uploads/${command.filename.trim()}"
        }
        return ObjectStoreResult(
            fileKey = normalizedKey,
            objectUrl = "s3://smartdoc-placeholder/$normalizedKey"
        )
    }

    override fun resolveObjectUrl(fileKey: String): String =
        "s3://smartdoc-placeholder/${fileKey.trim()}"
}

class ResourceNotFoundException(message: String) : RuntimeException(message)

@Entity
@Table(name = "documents")
class DocumentEntity(
    @Id
    @Column(length = 36)
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "file_key", nullable = false, length = 512)
    var fileKey: String = "",

    @Column(nullable = false, length = 255)
    var filename: String = "",

    @Column(nullable = false, length = 32)
    var status: String = "RECEIVED",

    @Column(name = "content_type", length = 128)
    var contentType: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
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

interface DocumentRepository : JpaRepository<DocumentEntity, String>

@Service
class DocumentService(
    private val documentRepository: DocumentRepository,
    private val objectStoragePort: ObjectStoragePort
) {
    fun create(request: DocumentCreateRequest): DocumentCreateResponse {
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
                filename = request.filename.trim(),
                contentType = request.contentType?.trim(),
                status = "RECEIVED"
            )
        )

        return toResponse(saved)
    }

    fun getById(documentId: String): DocumentCreateResponse {
        val found = documentRepository.findById(documentId)
            .orElseThrow { ResourceNotFoundException("document not found: $documentId") }
        return toResponse(found)
    }

    fun list(): List<DocumentCreateResponse> = documentRepository.findAll()
        .sortedByDescending { it.createdAt }
        .map(::toResponse)

    fun updateStatus(documentId: String, request: DocumentStatusUpdateRequest): DocumentCreateResponse {
        val normalizedStatus = request.status.trim().uppercase()
        require(normalizedStatus in ALLOWED_STATUSES) { "unsupported document status: $normalizedStatus" }

        val found = documentRepository.findById(documentId)
            .orElseThrow { ResourceNotFoundException("document not found: $documentId") }

        found.status = normalizedStatus
        return toResponse(documentRepository.save(found))
    }

    private fun toResponse(entity: DocumentEntity): DocumentCreateResponse =
        DocumentCreateResponse(
            documentId = entity.id,
            filename = entity.filename,
            fileKey = entity.fileKey,
            contentType = entity.contentType,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    companion object {
        private val ALLOWED_STATUSES = setOf(
            "RECEIVED",
            "ANALYSIS_QUEUED",
            "ANALYSIS_PROCESSING",
            "ANALYSIS_COMPLETED"
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
        @RequestBody request: DocumentCreateRequest
    ): DocumentCreateResponse {
        require(request.filename.isNotBlank()) { "filename must not be blank" }
        require(request.fileKey.isNotBlank()) { "fileKey must not be blank" }
        return documentService.create(request)
    }

    @GetMapping("/{id}")
    fun getDocument(@PathVariable id: String): DocumentCreateResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        return documentService.getById(id)
    }

    @GetMapping
    fun listDocuments(): List<DocumentCreateResponse> = documentService.list()

    @PatchMapping("/{id}/status")
    fun updateDocumentStatus(
        @PathVariable id: String,
        @RequestBody request: DocumentStatusUpdateRequest
    ): DocumentCreateResponse {
        require(id.isNotBlank()) { "id must not be blank" }
        require(request.status.isNotBlank()) { "status must not be blank" }
        return documentService.updateStatus(id, request)
    }

    @PostMapping("/{id}/status")
    fun postDocumentStatus(
        @PathVariable id: String,
        @RequestBody request: DocumentStatusUpdateRequest
    ): DocumentCreateResponse = updateDocumentStatus(id, request)
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
