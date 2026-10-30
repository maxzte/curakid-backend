package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.essential.logger
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NotificationConsumer(
    private val pushNotificationService: PushNotificationService
) {

    private val log = logger()

    @KafkaListener(
        topics = [NotificationTopics.NOTIFICATIONS],
        groupId = "\${spring.kafka.consumer.group-id:curakid-notification-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(event: NotificationEvent) {
        log.info("Received notification event from Kafka: id={}, userId={}, title={}", event.eventId, event.userId, event.title)
        
        val payload = PushNotificationDto(
            title = event.title,
            body = event.body,
            data = event.data
        )

        val delivered = pushNotificationService.sendNotificationToUser(
            userId = event.userId,
            notificationDto = payload,
            isUrgent = event.isUrgent
        )

        if (!delivered) {
            log.warn("Notification event {} could not be delivered (no active tokens or uninitialized FCM). Saved for later.", event.eventId)
        } else {
            log.info("Notification event {} successfully processed and dispatched via FCM", event.eventId)
        }
    }
}
