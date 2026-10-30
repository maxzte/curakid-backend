package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.essential.logger
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class NotificationProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {

    private val log = logger()

    fun enqueue(event: NotificationEvent) {
        log.info("Publishing notification event to Kafka: id={}, userId={}, title={}", event.eventId, event.userId, event.title)
        kafkaTemplate.send(NotificationTopics.NOTIFICATIONS, event.userId.toString(), event)
            .whenComplete { result, ex ->
                if (ex != null) {
                    log.error("Failed to send notification event {} to Kafka: {}", event.eventId, ex.message, ex)
                } else {
                    log.debug("Notification event {} sent to partition {} at offset {}", 
                        event.eventId, result.recordMetadata.partition(), result.recordMetadata.offset())
                }
            }
    }
}
