package com.misterio.curakid.feature.notification

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import com.misterio.curakid.core.essential.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.writeTx
import org.springframework.context.annotation.Lazy
import java.util.UUID

@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    @Lazy(false)
    private val firebaseConfig: FirebaseConfig,
    private val transactionManager: PlatformTransactionManager? = null
) {

    private val log = logger()

    companion object {
        const val TOPIC_CATALOG_SYNC = "curakid_catalog_sync"
    }

    fun sendNotificationToUser(userId: UUID, notificationDto: PushNotificationDto, isUrgent: Boolean = false): Boolean {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp is not initialized. Skipping push notification to user {}: {}", userId, notificationDto.title)
            return false
        }

        val activeTokens = deviceTokenRepository.findAllByUserIdAndIsActiveTrue(userId)
        if (activeTokens.isEmpty()) {
            log.debug("No active device tokens found for user {}", userId)
            return false
        }

        val messaging = FirebaseMessaging.getInstance()
        var deliveredAtLeastOnce = false

        for (deviceToken in activeTokens) {
            val androidConfig = AndroidConfig.builder()
                .setPriority(if (isUrgent) AndroidConfig.Priority.HIGH else AndroidConfig.Priority.NORMAL)
                .setNotification(
                    AndroidNotification.builder()
                        .setChannelId("curakid_fcm_channel")
                        .build()
                )
                .build()

            val messageBuilder = Message.builder()
                .setToken(deviceToken.token)
                .setAndroidConfig(androidConfig)
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
                deliveredAtLeastOnce = true
                log.info("Successfully sent push notification to token id {}: messageId={}", deviceToken.id, response)
            } catch (e: FirebaseMessagingException) {
                log.warn("Failed to send push notification to token id {}: {} (errorCode={})", deviceToken.id, e.message, e.messagingErrorCode)
                // If token is invalid or registration is uninstalled, deactivate token
                if (e.messagingErrorCode?.name in listOf("UNREGISTERED", "INVALID_ARGUMENT")) {
                    log.info("Deactivating invalid or expired device token {}", deviceToken.id)
                    deviceToken.isActive = false
                    transactionManager?.writeTx {
                        deviceTokenRepository.save(deviceToken)
                    } ?: deviceTokenRepository.save(deviceToken)
                }
                // If it's a transient server or connection error, bubble up for retry
                if (e.messagingErrorCode?.name in listOf("UNAVAILABLE", "INTERNAL")) {
                    throw e
                }
            } catch (e: Exception) {
                log.error("Unexpected error sending push notification: {}", e.message, e)
            }
        }
        return deliveredAtLeastOnce
    }

    fun broadcastSilentSync(data: Map<String, String>) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp is not initialized. Skipping broadcastSilentSync.")
            return
        }

        val messaging = FirebaseMessaging.getInstance()

        // High-scale broadcast via FCM Topic: 1 API call handles 1M+ subscribers without OOM
        try {
            val message = Message.builder()
                .setTopic(TOPIC_CATALOG_SYNC)
                .putAllData(data)
                .build()

            val response = messaging.send(message)
            log.info("Broadcasted silent sync message to topic {}: messageId={}", TOPIC_CATALOG_SYNC, response)
        } catch (e: Exception) {
            log.error("Failed to broadcast silent sync to topic {}: {}", TOPIC_CATALOG_SYNC, e.message, e)
        }
    }
}
