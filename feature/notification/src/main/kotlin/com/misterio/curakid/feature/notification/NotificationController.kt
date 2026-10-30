package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.currentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val deviceTokenService: DeviceTokenService,
    private val pushNotificationService: PushNotificationService,
    private val eventReminderScheduler: EventReminderScheduler
) {

    @PostMapping("/device-token")
    fun registerToken(
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
        auth: Authentication
    ): ResponseEntity<DeviceTokenResponse> {
        val response = deviceTokenService.registerToken(auth.currentUser(), request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/device-token")
    fun removeToken(
        @RequestParam token: String,
        auth: Authentication
    ): ResponseEntity<Void> {
        deviceTokenService.removeToken(auth.currentUser().id, token)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/test-push")
    fun sendTestPush(
        @RequestParam userId: UUID,
        @RequestParam(required = false, defaultValue = "CuraKid") title: String,
        @RequestParam(required = false, defaultValue = "Тестове пуш-сповіщення доставлено!") body: String
    ): ResponseEntity<Map<String, String>> {
        pushNotificationService.sendNotificationToUser(
            userId,
            PushNotificationDto(
                title = title,
                body = body,
                data = mapOf("type" to "TEST_NOTIFICATION")
            )
        )
        return ResponseEntity.ok(mapOf("status" to "SENT", "userId" to userId.toString()))
    }

    @PostMapping("/trigger-reminders")
    fun triggerReminders(): ResponseEntity<Map<String, String>> {
        eventReminderScheduler.sendUpcomingEventReminders()
        return ResponseEntity.ok(mapOf("status" to "TRIGGERED"))
    }
}
