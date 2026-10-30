package com.misterio.curakid.feature.notification

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.notification.DeviceToken
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import com.misterio.curakid.core.essential.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import java.time.Instant
import java.util.UUID

@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userRepository: UserRepository,
    private val transactionManager: PlatformTransactionManager
) {

    private val log = logger()

    fun registerToken(user: User, request: RegisterDeviceTokenRequest): DeviceTokenResponse {
        val (managedUser, existing) = requireNotNull(transactionManager.readTx {
            val u = userRepository.findById(user.id)
                .orElseThrow { NoSuchElementException("User not found") }
            val tok = deviceTokenRepository.findByToken(request.token)
            Pair(u, tok)
        })

        val deviceToken = if (existing != null) {
            val existingUserId = existing.user?.id
            if (existingUserId != null && existingUserId != managedUser.id) {
                log.info("Reassigning device token from previous user {} to user {}", existingUserId, managedUser.id)
            }
            existing.user = managedUser
            existing.deviceType = request.deviceType
            existing.isActive = true
            existing.lastActiveAt = Instant.now()
            existing
        } else {
            DeviceToken(
                user = managedUser,
                token = request.token,
                deviceType = request.deviceType,
                lastActiveAt = Instant.now()
            )
        }

        val saved = requireNotNull(transactionManager.writeTx {
            deviceTokenRepository.save(deviceToken)
        })
        log.info("Registered/updated device token for user: {}", managedUser.id)

        // Asynchronously subscribe token to catalog sync topic (strictly outside DB transaction)
        if (FirebaseApp.getApps().isNotEmpty()) {
            try {
                FirebaseMessaging.getInstance().subscribeToTopicAsync(
                    listOf(saved.token),
                    PushNotificationService.TOPIC_CATALOG_SYNC
                )
            } catch (e: Exception) {
                log.warn("Failed to subscribe token to topic {}: {}", PushNotificationService.TOPIC_CATALOG_SYNC, e.message)
            }
        }

        return DeviceTokenResponse(
            id = saved.id,
            token = saved.token,
            deviceType = saved.deviceType,
            lastActiveAt = saved.lastActiveAt
        )
    }

    fun removeToken(userId: UUID, token: String) {
        val existing = transactionManager.readTx {
            deviceTokenRepository.findByToken(token)
        }

        if (existing != null && existing.user?.id == userId) {
            existing.isActive = false
            transactionManager.writeTx {
                deviceTokenRepository.save(existing)
            }
            log.info("Deactivated device token for user: {}", userId)

            if (FirebaseApp.getApps().isNotEmpty()) {
                try {
                    FirebaseMessaging.getInstance().unsubscribeFromTopicAsync(
                        listOf(token),
                        PushNotificationService.TOPIC_CATALOG_SYNC
                    )
                } catch (e: Exception) {
                    log.warn("Failed to unsubscribe token from topic: {}", e.message)
                }
            }
        }
    }
}
