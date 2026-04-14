package com.smartdoc.notification

import org.hamcrest.Matchers.blankOrNullString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var notificationEventRepository: NotificationEventRepository

    @BeforeEach
    fun setUp() {
        notificationEventRepository.deleteAll()
    }

    @Test
    fun `dispatches and lists notification event`() {
        val created = mockMvc.perform(
            post("/api/v1/notifications/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "documentId": "document-1",
                      "channel": "slack",
                      "message": "review requested"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.documentId").value("document-1"))
            .andExpect(jsonPath("$.channel").value("slack"))
            .andExpect(jsonPath("$.status").value("DISPATCHED"))
            .andReturn()

        val eventId = Regex(""""eventId":"([^"]+)"""")
            .find(created.response.contentAsString)
            ?.groupValues
            ?.get(1)
            ?: error("eventId not found")

        mockMvc.perform(get("/api/v1/notifications/events/$eventId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.eventId").value(eventId))

        mockMvc.perform(get("/api/v1/notifications/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].eventId").value(eventId))
    }

    @Test
    fun `returns standard validation error`() {
        mockMvc.perform(
            post("/api/v1/notifications/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"","channel":"slack","message":"review requested"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.traceId").value(not(blankOrNullString())))
    }
}
