package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.notification.DeviceToken
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import com.misterio.curakid.core.essential.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository
) {

    private val log = logger()

    @Transactional
    fun registerToken(user: User, request: RegisterDeviceTokenRequest): DeviceTokenResponse {
        val existing = deviceTokenRepository.findByToken(request.token)

        val deviceToken = if (existing != null) {
            existing.user = user
            existing.deviceType = request.deviceType ?: "ANDROID"
            existing.isActive = true
            existing.lastActiveAt = Instant.now()
            existing
        } else {
            DeviceToken(
                user = user,
                token = request.token,
                deviceType = request.deviceType ?: "ANDROID",
                lastActiveAt = Instant.now()
            )
        }

        val saved = deviceTokenRepository.save(deviceToken)
        log.info("Registered/updated device token for user: {}", user.id)

        return DeviceTokenResponse(
            id = saved.id,
            token = saved.token,
            deviceType = saved.deviceType,
            lastActiveAt = saved.lastActiveAt
        )
    }

    @Transactional
    fun removeToken(userId: UUID, token: String) {
        val existing = deviceTokenRepository.findByToken(token)
        if (existing != null && existing.user?.id == userId) {
            existing.isActive = false
            deviceTokenRepository.save(existing)
            log.info("Deactivated device token for user: {}", userId)
        }
    }
}
