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
    private val testApiKey = "secret-internal-key"

    private val user = User(
        email = "test@curakid.com",
        passwordHash = "hash123",
        name = "Test Parent"
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
            eventReminderScheduler = eventReminderScheduler,
            internalApiKey = testApiKey
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

        whenever(deviceTokenService.registerToken(eq(user), any())).thenReturn(
            DeviceTokenResponse(
                id = tokenId,
                token = tokenString,
                deviceType = "ANDROID",
                lastActiveAt = Instant.now()
            )
        )

        mockMvc.perform(
            post("/api/v1/notifications/device-token")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(tokenId.toString()))
            .andExpect(jsonPath("$.token").value(tokenString))
            .andExpect(jsonPath("$.deviceType").value("ANDROID"))

        verify(deviceTokenService).registerToken(eq(user), argThat {
            this.token == tokenString && this.deviceType == "ANDROID"
        })
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
    fun `POST test-push with valid api key returns 200 and sends push`() {
        mockMvc.perform(
            post("/api/v1/notifications/test-push")
                .header("X-Internal-Api-Key", testApiKey)
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
            }
        )
    }

    @Test
    fun `POST test-push with invalid api key returns 403 Forbidden`() {
        mockMvc.perform(
            post("/api/v1/notifications/test-push")
                .header("X-Internal-Api-Key", "wrong-key")
                .param("userId", user.id.toString())
        )
            .andExpect(status().isForbidden)

        verify(pushNotificationService, never()).sendNotificationToUser(any(), any())
    }

    @Test
    fun `POST trigger-reminders with valid api key triggers scheduler`() {
        mockMvc.perform(
            post("/api/v1/notifications/trigger-reminders")
                .header("X-Internal-Api-Key", testApiKey)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("TRIGGERED"))

        verify(eventReminderScheduler).sendUpcomingEventReminders()
    }
}
