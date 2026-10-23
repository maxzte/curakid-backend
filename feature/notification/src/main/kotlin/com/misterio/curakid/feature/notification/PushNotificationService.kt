package com.misterio.curakid.feature.notification

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import com.misterio.curakid.core.essential.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    @org.springframework.context.annotation.Lazy(false)
    private val firebaseConfig: FirebaseConfig
) {

    private val log = logger()

    @Transactional
    fun sendNotificationToUser(userId: UUID, notificationDto: PushNotificationDto) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp is not initialized. Skipping push notification to user {}: {}", userId, notificationDto.title)
            return
        }

        val activeTokens = deviceTokenRepository.findAllByUserIdAndIsActiveTrue(userId)
        if (activeTokens.isEmpty()) {
            log.debug("No active device tokens found for user {}", userId)
            return
        }

        val messaging = FirebaseMessaging.getInstance()

        for (deviceToken in activeTokens) {
            val messageBuilder = Message.builder()
                .setToken(deviceToken.token)
                .setNotification(
                    Notification.builder()
                        .setTitle(notificationDto.title)
                        .setBody(notificationDto.body)
                        .build()
                )

            if (notificationDto.data.isNotEmpty()) {
                messageBuilder.putAllData(notificationDto.data)
            }

            try {
                val response = messaging.send(messageBuilder.build())
                log.info("Successfully sent push notification to token id {}: messageId={}", deviceToken.id, response)
            } catch (e: FirebaseMessagingException) {
                log.warn("Failed to send push notification to token id {}: {} (errorCode={})", deviceToken.id, e.message, e.messagingErrorCode)
                // If token is invalid or registration is uninstalled, deactivate token
                if (e.messagingErrorCode?.name in listOf("UNREGISTERED", "INVALID_ARGUMENT")) {
                    log.info("Deactivating invalid or expired device token {}", deviceToken.id)
                    deviceToken.isActive = false
                    deviceTokenRepository.save(deviceToken)
                }
            } catch (e: Exception) {
                log.error("Unexpected error sending push notification: {}", e.message, e)
            }
        }
    }
}
