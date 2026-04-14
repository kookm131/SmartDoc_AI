package com.smartdoc.document

import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.blankOrNullString
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
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(properties = ["smartdoc.local-upload-dir=/tmp/smartdoc-document-test-uploads"])
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
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"filename":"","fileKey":"uploads/contract.pdf"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
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
}
