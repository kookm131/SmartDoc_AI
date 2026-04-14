package com.smartdoc.document

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
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
import jakarta.servlet.http.HttpServletRequest
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

data class ApiErrorResponse(
    val timestamp: Instant,
    val path: String,
    val code: String,
    val message: String,
    val traceId: String
)

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
    private val documentRepository: DocumentRepository
) {
    fun create(request: DocumentCreateRequest): DocumentCreateResponse {
        val saved = documentRepository.save(
            DocumentEntity(
                fileKey = request.fileKey.trim(),
                filename = request.filename.trim(),
                contentType = request.contentType?.trim(),
                status = "RECEIVED"
            )
        )

        return DocumentCreateResponse(
            documentId = saved.id,
            filename = saved.filename,
            fileKey = saved.fileKey,
            contentType = saved.contentType,
            status = saved.status,
            createdAt = saved.createdAt,
            updatedAt = saved.updatedAt
        )
    }

    fun getById(documentId: String): DocumentCreateResponse {
        val found = documentRepository.findById(documentId)
            .orElseThrow { ResourceNotFoundException("document not found: $documentId") }
        return DocumentCreateResponse(
            documentId = found.id,
            filename = found.filename,
            fileKey = found.fileKey,
            contentType = found.contentType,
            status = found.status,
            createdAt = found.createdAt,
            updatedAt = found.updatedAt
        )
    }

    fun list(): List<DocumentCreateResponse> = documentRepository.findAll()
        .sortedByDescending { it.createdAt }
        .map {
            DocumentCreateResponse(
                documentId = it.id,
                filename = it.filename,
                fileKey = it.fileKey,
                contentType = it.contentType,
                status = it.status,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt
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
