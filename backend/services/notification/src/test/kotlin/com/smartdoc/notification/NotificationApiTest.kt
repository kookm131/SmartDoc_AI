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

    @Autowired
    private lateinit var notificationRuleRepository: NotificationRuleRepository

    @BeforeEach
    fun setUp() {
        notificationEventRepository.deleteAll()
        notificationRuleRepository.deleteAll()
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

    @Test
    fun `creates and lists notification rule`() {
        mockMvc.perform(
            post("/api/v1/notifications/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keyword":"계약","channel":"slack","enabled":true}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.keyword").value("계약"))
            .andExpect(jsonPath("$.channel").value("slack"))
            .andExpect(jsonPath("$.enabled").value(true))

        mockMvc.perform(get("/api/v1/notifications/rules"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].keyword").value("계약"))

        mockMvc.perform(
            post("/api/v1/notifications/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keyword":"계약","channel":"slack","enabled":false}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.enabled").value(false))

        check(notificationRuleRepository.findAll().size == 1) {
            "duplicate rule should update existing row"
        }
    }

    @Test
    fun `dispatches automatic notification only when enabled rule matches`() {
        notificationRuleRepository.save(
            NotificationRuleEntity(
                keyword = "계약",
                channel = "slack",
                enabled = true
            )
        )

        mockMvc.perform(
            post("/api/v1/notifications/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"document-1","keywords":["계약","검토"],"riskScore":24}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.documentId").value("document-1"))
            .andExpect(jsonPath("$.channel").value("slack"))
            .andExpect(jsonPath("$.message").value("분석 완료: '계약' 키워드 규칙이 매칭되었습니다. 위험 점수 24점"))

        check(notificationEventRepository.findAll().size == 1) {
            "expected one notification event"
        }
    }

    @Test
    fun `does not dispatch automatic notification for disabled rule`() {
        notificationRuleRepository.save(
            NotificationRuleEntity(
                keyword = "계약",
                channel = "slack",
                enabled = false
            )
        )

        mockMvc.perform(
            post("/api/v1/notifications/dispatch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"documentId":"document-1","keywords":["계약"],"riskScore":24}""")
        )
            .andExpect(status().isNoContent)

        check(notificationEventRepository.findAll().isEmpty()) {
            "disabled rule should not create notification event"
        }
    }
}
