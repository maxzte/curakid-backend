package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.event.EventStatus
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.essential.common.events.NotificationPayloadConstants
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class EventReminderSchedulerTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var scheduler: EventReminderScheduler

    private val user = User(
        email = "parent@curakid.com",
        passwordHash = "hash",
        name = "Олена"
    ).apply { id = UUID.randomUUID() }

    private val child = Child(
        user = user,
        name = "Марко"
    ).apply { id = UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        eventRepository = mock()
        pushNotificationService = mock()
        scheduler = EventReminderScheduler(eventRepository, pushNotificationService)
    }

    @Test
    fun `sendUpcomingEventReminders finds pending events, sends push with deeplink payload, and marks reminderSent`() {
        val eventId = UUID.randomUUID()
        val eventDate = Instant.now().plus(12, ChronoUnit.HOURS)
        val event = Event(
            title = "Планова вакцинація КПК-1",
            type = EventType.VACCINE,
            date = eventDate,
            child = child,
            status = EventStatus.PLANNED,
            reminderSent = false
        ).apply { id = eventId }

        whenever(eventRepository.findPendingReminders(any(), any())).thenReturn(listOf(event))

        scheduler.sendUpcomingEventReminders()

        // 1. Verify push notification sent with deep link payload
        verify(pushNotificationService).sendNotificationToUser(
            eq(user.id),
            argThat {
                this.title.contains("Марко") &&
                this.body.contains("Планова вакцинація КПК-1") &&
                this.data[NotificationPayloadConstants.KEY_TYPE] == NotificationPayloadConstants.TYPE_EVENT_REMINDER &&
                this.data[NotificationPayloadConstants.KEY_EVENT_ID] == eventId.toString() &&
                this.data[NotificationPayloadConstants.KEY_CHILD_ID] == child.id.toString()
            }
        )

        // 2. Verify idempotency: event is marked reminderSent = true and saved
        assertTrue(event.reminderSent)
        verify(eventRepository).save(event)
    }

    @Test
    fun `sendUpcomingEventReminders does nothing when no pending events found`() {
        whenever(eventRepository.findPendingReminders(any(), any())).thenReturn(emptyList())

        scheduler.sendUpcomingEventReminders()

        verifyNoInteractions(pushNotificationService)
        verify(eventRepository, never()).save(any())
    }
}
