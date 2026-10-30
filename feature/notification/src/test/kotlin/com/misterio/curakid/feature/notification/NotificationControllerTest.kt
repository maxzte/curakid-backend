package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.auth.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class NotificationControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var deviceTokenService: DeviceTokenService
    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var eventReminderScheduler: EventReminderScheduler

    private val user = User(
        email = "test@test.com",
        passwordHash = "hash",
        name = "Test"
    ).apply {
        id = UUID.randomUUID()
    }

    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())

    @BeforeEach
    fun setUp() {
        deviceTokenService = mock()
        pushNotificationService = mock()
        eventReminderScheduler = mock()
        val controller = NotificationController(
            deviceTokenService = deviceTokenService,
            pushNotificationService = pushNotificationService,
            eventReminderScheduler = eventReminderScheduler
        )
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `POST registerToken returns 200 with device token response`() {
        val tokenId = UUID.randomUUID()
        val tokenString = "valid_fcm_token_123"
        val requestJson = """
            {
                "token": "$tokenString",
                "deviceType": "ANDROID"
            }
        """.trimIndent()

        val response = DeviceTokenResponse(
            id = tokenId,
            token = tokenString,
            deviceType = "ANDROID",
            lastActiveAt = Instant.now()
        )

        whenever(deviceTokenService.registerToken(any(), any())).thenReturn(response)

        mockMvc.perform(
            post("/api/v1/notifications/device-token")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value(tokenString))

        verify(deviceTokenService).registerToken(eq(user), any())
    }

    @Test
    fun `POST registerToken with blank token returns 400 Bad Request`() {
        val requestJson = """
            {
                "token": "  ",
                "deviceType": "ANDROID"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/notifications/device-token")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isBadRequest)

        verify(deviceTokenService, never()).registerToken(any(), any())
    }

    @Test
    fun `DELETE device-token returns 204 No Content`() {
        val tokenString = "token_to_remove"

        mockMvc.perform(
            delete("/api/v1/notifications/device-token")
                .principal(auth)
                .param("token", tokenString)
        )
            .andExpect(status().isNoContent)

        verify(deviceTokenService).removeToken(user.id, tokenString)
    }

    @Test
    fun `POST test-push returns 200 and sends push`() {
        mockMvc.perform(
            post("/api/v1/notifications/test-push")
                .param("userId", user.id.toString())
                .param("title", "Custom Title")
                .param("body", "Custom Body")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("SENT"))

        verify(pushNotificationService).sendNotificationToUser(
            eq(user.id),
            argThat {
                this.title == "Custom Title" && this.body == "Custom Body"
            },
            any()
        )
    }

    @Test
    fun `POST trigger-reminders triggers scheduler`() {
        mockMvc.perform(
            post("/api/v1/notifications/trigger-reminders")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("TRIGGERED"))

        verify(eventReminderScheduler).sendUpcomingEventReminders()
    }
}
