package com.misterio.curakid.feature.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationConsumerTest {

    @Mock
    private lateinit var pushNotificationService: PushNotificationService

    private lateinit var consumer: NotificationConsumer

    @BeforeEach
    fun setUp() {
        consumer = NotificationConsumer(pushNotificationService)
    }

    @Test
    fun `consume should dispatch push notification payload with correct fields`() {
        // Given
        val userId = UUID.randomUUID()
        val event = NotificationEvent(
            userId = userId,
            title = "Test Reminder",
            body = "Vaccine appointment tomorrow",
            data = mapOf("key" to "value"),
            isUrgent = true
        )

        whenever(pushNotificationService.sendNotificationToUser(eq(userId), any(), eq(true)))
            .thenReturn(true)

        // When
        consumer.consume(event)

        // Then
        val captor = argumentCaptor<PushNotificationDto>()
        verify(pushNotificationService).sendNotificationToUser(eq(userId), captor.capture(), eq(true))

        val captured = captor.firstValue
        assertEquals("Test Reminder", captured.title)
        assertEquals("Vaccine appointment tomorrow", captured.body)
        assertEquals(mapOf("key" to "value"), captured.data)
    }

    @Test
    fun `consume should handle undelivered status without throwing`() {
        // Given
        val userId = UUID.randomUUID()
        val event = NotificationEvent(
            userId = userId,
            title = "Notice",
            body = "Check your profile",
            isUrgent = false
        )

        whenever(pushNotificationService.sendNotificationToUser(eq(userId), any(), eq(false)))
            .thenReturn(false)

        // When
        consumer.consume(event)

        // Then
        verify(pushNotificationService).sendNotificationToUser(eq(userId), any(), eq(false))
    }
}
