package com.misterio.curakid.feature.notification

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class RegisterDeviceTokenRequest(
    @field:NotBlank(message = "Token cannot be blank")
    val token: String,
    val deviceType: String = "ANDROID"
)

data class DeviceTokenResponse(
    val id: UUID,
    val token: String,
    val deviceType: String,
    val lastActiveAt: Instant
)

data class PushNotificationDto(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap()
)
