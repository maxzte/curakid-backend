package com.misterio.curakid.feature.notification

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import java.util.UUID
import java.util.concurrent.CompletableFuture

class NotificationProducerTest {

    private lateinit var kafkaTemplate: KafkaTemplate<String, Any>
    private lateinit var producer: NotificationProducer

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mock()
        producer = NotificationProducer(kafkaTemplate)
    }

    @Test
    fun `enqueue sends event to Kafka topic with userId partition key`() {
        // Arrange
        val userId = UUID.randomUUID()
        val event = NotificationEvent(
            eventId = UUID.randomUUID(),
            userId = userId,
            title = "Vaccine Reminder",
            body = "Child vaccination scheduled tomorrow",
            data = mapOf("childId" to UUID.randomUUID().toString())
        )
        whenever(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(mock()))

        // Act
        producer.enqueue(event)

        // Assert
        verify(kafkaTemplate).send(
            eq(NotificationTopics.NOTIFICATIONS),
            eq(userId.toString()),
            eq(event)
        )
    }

    @Test
    fun `enqueue handles kafka send failure gracefully without throwing`() {
        // Arrange
        val userId = UUID.randomUUID()
        val event = NotificationEvent(
            eventId = UUID.randomUUID(),
            userId = userId,
            title = "Warning",
            body = "System maintenance"
        )
        val future = CompletableFuture<org.springframework.kafka.support.SendResult<String, Any>>()
        future.completeExceptionally(RuntimeException("Kafka broker unavailable"))
        whenever(kafkaTemplate.send(any(), any(), any())).thenReturn(future)

        // Act & Assert (should not throw exception)
        producer.enqueue(event)

        verify(kafkaTemplate).send(
            eq(NotificationTopics.NOTIFICATIONS),
            eq(userId.toString()),
            eq(event)
        )
    }
}
