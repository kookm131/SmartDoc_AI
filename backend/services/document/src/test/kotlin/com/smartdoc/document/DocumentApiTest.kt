package com.smartdoc.document

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.blankOrNullString
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(
    properties = [
        "smartdoc.local-upload-dir=/tmp/smartdoc-document-test-uploads",
        "smartdoc.max-upload-bytes=64"
    ]
)
@AutoConfigureMockMvc
class DocumentApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var documentRepository: DocumentRepository

    @BeforeEach
    fun setUp() {
        documentRepository.deleteAll()
    }

    @Test
    fun `creates document and updates analysis status`() {
        val created = mockMvc.perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "filename": "contract.pdf",
                      "fileKey": "uploads/contract.pdf",
                      "contentType": "application/pdf"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andReturn()

        val documentId = Regex(""""documentId":"([^"]+)"""")
            .find(created.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("documentId not found")

        mockMvc.perform(
            patch("/api/v1/documents/$documentId/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"ANALYSIS_COMPLETED"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.documentId").value(documentId))
            .andExpect(jsonPath("$.status").value("ANALYSIS_COMPLETED"))
    }

    @Test
    fun `returns standard validation error`() {
        mockMvc.perform(
            post("/api/v1/documents")
                .header("X-SmartDoc-Trace-Id", "trace-doc-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"","fileKey":"uploads/contract.pdf"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value("trace-doc-1"))
    }

    @Test
    fun `uploads document file and creates metadata`() {
        val file = MockMultipartFile(
            "file",
            "contract.pdf",
            "application/pdf",
            "hello smartdoc".toByteArray()
        )

        val uploaded = mockMvc.perform(multipart("/api/v1/documents/upload").file(file))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.filename").value("contract.pdf"))
            .andExpect(jsonPath("$.fileKey").value("uploads/contract.pdf"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andReturn()

        val documentId = Regex(""""documentId":"([^"]+)"""")
            .find(uploaded.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("documentId not found")

        check(documentRepository.findAll().size == 1) {
            "expected one document row"
        }
        check(Files.exists(Path.of("/tmp/smartdoc-document-test-uploads/uploads/contract.pdf"))) {
            "expected uploaded file to be stored"
        }

        mockMvc.perform(get("/api/v1/documents/$documentId/content"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.documentId").value(documentId))
            .andExpect(jsonPath("$.textContent").value(nullValue()))
    }

    @Test
    fun `returns uploaded text content for text document`() {
        val file = MockMultipartFile(
            "file",
            "contract.txt",
            "text/plain",
            "긴급 계약 검토 알림".toByteArray()
        )

        val uploaded = mockMvc.perform(multipart("/api/v1/documents/upload").file(file))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.filename").value("contract.txt"))
            .andExpect(jsonPath("$.fileKey").value("uploads/contract.txt"))
            .andReturn()

        val documentId = Regex(""""documentId":"([^"]+)"""")
            .find(uploaded.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("documentId not found")

        mockMvc.perform(get("/api/v1/documents/$documentId/content"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.textContent").value("긴급 계약 검토 알림"))
    }

    @Test
    fun `extracts text content for valid pdf stored locally`() {
        val uploadRoot = Path.of("/tmp/smartdoc-document-test-uploads")
        val target = uploadRoot.resolve("uploads/contract.pdf")
        Files.createDirectories(target.parent)
        Files.write(target, createPdfBytes("urgent contract review"))

        mockMvc.perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "filename":"contract.pdf",
                      "fileKey":"uploads/contract.pdf",
                      "contentType":"application/pdf"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)

        val documentId = documentRepository.findAll().first().id

        mockMvc.perform(get("/api/v1/documents/$documentId/content"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.textContent").value(containsString("contract")))
    }

    @Test
    fun `separates documents by owner header`() {
        val aliceCreated = mockMvc.perform(
            post("/api/v1/documents")
                .header("X-SmartDoc-User-Id", "alice-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"alice.pdf","fileKey":"uploads/alice.pdf"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerUserId").value("alice-user"))
            .andReturn()

        val aliceDocumentId = Regex(""""documentId":"([^"]+)"""")
            .find(aliceCreated.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("documentId not found")

        mockMvc.perform(
            post("/api/v1/documents")
                .header("X-SmartDoc-User-Id", "bob-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"bob.pdf","fileKey":"uploads/bob.pdf"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.ownerUserId").value("bob-user"))

        mockMvc.perform(
            get("/api/v1/documents")
                .header("X-SmartDoc-User-Id", "alice-user")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].documentId").value(aliceDocumentId))

        mockMvc.perform(
            get("/api/v1/documents/$aliceDocumentId")
                .header("X-SmartDoc-User-Id", "bob-user")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `archives document and hides it from default list`() {
        val created = mockMvc.perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"archive-me.pdf","fileKey":"uploads/archive-me.pdf"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val documentId = Regex(""""documentId":"([^"]+)"""")
            .find(created.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("documentId not found")

        mockMvc.perform(post("/api/v1/documents/$documentId/archive"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.documentId").value(documentId))
            .andExpect(jsonPath("$.status").value("ARCHIVED"))
            .andExpect(jsonPath("$.archivedAt").value(not(blankOrNullString())))

        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        mockMvc.perform(get("/api/v1/documents/archived"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].documentId").value(documentId))

        mockMvc.perform(
            post("/api/v1/documents/$documentId/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status":"ANALYSIS_COMPLETED"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ARCHIVED"))
    }

    @Test
    fun `returns validation error for empty upload`() {
        val file = MockMultipartFile(
            "file",
            "empty.pdf",
            "application/pdf",
            ByteArray(0)
        )

        mockMvc.perform(multipart("/api/v1/documents/upload").file(file))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    @Test
    fun `returns validation error for oversized upload`() {
        val file = MockMultipartFile(
            "file",
            "large.txt",
            "text/plain",
            ByteArray(65) { 'x'.code.toByte() }
        )

        mockMvc.perform(multipart("/api/v1/documents/upload").file(file))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("file size must be less than or equal to 64 bytes"))
    }

    @Test
    fun `returns validation error for unsupported upload extension`() {
        val file = MockMultipartFile(
            "file",
            "script.exe",
            "application/octet-stream",
            "binary".toByteArray()
        )

        mockMvc.perform(multipart("/api/v1/documents/upload").file(file))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("unsupported file extension: .exe"))
    }

    @Test
    fun `returns validation error for mismatched content type`() {
        val file = MockMultipartFile(
            "file",
            "contract.pdf",
            "text/plain",
            "not actually a pdf".toByteArray()
        )

        mockMvc.perform(multipart("/api/v1/documents/upload").file(file))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("unsupported contentType text/plain for .pdf file"))
    }

    private fun createPdfBytes(text: String): ByteArray {
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)

            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(72f, 700f)
                content.showText(text)
                content.endText()
            }

            val out = ByteArrayOutputStream()
            document.save(out)
            return out.toByteArray()
        }
    }
}
