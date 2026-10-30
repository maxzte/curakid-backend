package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.event.EventStatus
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

    @Mock
    private lateinit var eventRepository: EventRepository

    @Mock
    private lateinit var childRepository: ChildRepository

    @Mock
    private lateinit var vaccineEventRepository: VaccineEventRepository

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var eventService: EventService

    private val user = User(email = "user@example.com", name = "User").apply { id = UUID.randomUUID() }
    private val otherUser = User(email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
    private val child = Child(user = user, name = "Ivan").apply { id = UUID.randomUUID() }
    private val otherChild = Child(user = otherUser, name = "Other Child").apply { id = UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        eventService = EventService(
            eventRepository,
            childRepository,
            vaccineEventRepository,
            transactionManager
        )
    }

    @Test
    fun `createEvent should persist and return event when child belongs to user`() {
        // Given
        val eventId = UUID.randomUUID()
        val now = Instant.parse("2026-06-01T10:00:00Z")
        val request = EventRequest(
            id = eventId,
            childId = child.id!!,
            title = "Pediatrician Visit",
            type = EventType.DOCTOR,
            date = now,
            location = "Clinic A",
            description = "Routine checkup",
            status = EventStatus.PLANNED
        )

        whenever(childRepository.findByIdAndUser(child.id!!, user)).thenReturn(Optional.of(child))
        whenever(eventRepository.findById(eventId)).thenReturn(Optional.empty())
        whenever(eventRepository.save(any<Event>())).thenAnswer { it.arguments[0] as Event }

        // When
        val response = eventService.createEvent(user, request)

        // Then
        assertEquals(eventId, response.id)
        assertEquals(child.id, response.childId)
        assertEquals("Pediatrician Visit", response.title)
        verify(eventRepository).save(any())
    }

    @Test
    fun `createEvent should throw NoSuchElementException and never save when child does not belong to user`() {
        // Given
        val request = EventRequest(
            id = UUID.randomUUID(),
            childId = otherChild.id!!,
            title = "Unauthorized Visit",
            type = EventType.DOCTOR,
            date = Instant.now()
        )
        whenever(childRepository.findByIdAndUser(otherChild.id!!, user)).thenReturn(Optional.empty())

        // When / Then
        assertThrows(NoSuchElementException::class.java) {
            eventService.createEvent(user, request)
        }
        verify(eventRepository, never()).save(any())
    }

    @Test
    fun `createEvent should throw ResponseStatusException CONFLICT and never save when event ID belongs to another user`() {
        // Given
        val collidingEventId = UUID.randomUUID()
        val request = EventRequest(
            id = collidingEventId,
            childId = child.id!!,
            title = "Colliding Event",
            type = EventType.USER,
            date = Instant.now()
        )
        val foreignExistingEvent = Event(
            title = "Foreign Event",
            type = EventType.USER,
            date = Instant.now(),
            child = otherChild
        ).apply { id = collidingEventId }

        whenever(childRepository.findByIdAndUser(child.id!!, user)).thenReturn(Optional.of(child))
        whenever(eventRepository.findById(collidingEventId)).thenReturn(Optional.of(foreignExistingEvent))

        // When / Then
        assertThrows(ResponseStatusException::class.java) {
            eventService.createEvent(user, request)
        }
        verify(eventRepository, never()).save(any())
    }

    @Test
    fun `updateEvent should throw NoSuchElementException and never save when event belongs to another user`() {
        // Given
        val foreignEventId = UUID.randomUUID()
        val updateRequest = UpdateEventRequest(title = "Hijacked Title")
        whenever(eventRepository.findByIdAndChildUser(foreignEventId, user)).thenReturn(Optional.empty())

        // When / Then
        assertThrows(NoSuchElementException::class.java) {
            eventService.updateEvent(child.id!!, foreignEventId, user, updateRequest)
        }
        verify(eventRepository, never()).save(any())
    }

    @Test
    fun `deleteEvent should populate deletedAt and save when event belongs to user`() {
        // Given
        val eventId = UUID.randomUUID()
        val existingEvent = Event(
            title = "To Delete",
            type = EventType.USER,
            date = Instant.now(),
            child = child
        ).apply { id = eventId }
        whenever(eventRepository.findByIdAndChildUser(eventId, user)).thenReturn(Optional.of(existingEvent))

        // When
        eventService.deleteEvent(child.id!!, eventId, user)

        // Then
        val captor = argumentCaptor<Event>()
        verify(eventRepository).save(captor.capture())
        assertNotNull(captor.firstValue.deletedAt)
    }

    @Test
    fun `deleteEvent should throw NoSuchElementException and never save when event belongs to another user`() {
        // Given
        val foreignEventId = UUID.randomUUID()
        whenever(eventRepository.findByIdAndChildUser(foreignEventId, user)).thenReturn(Optional.empty())

        // When / Then
        assertThrows(NoSuchElementException::class.java) {
            eventService.deleteEvent(child.id!!, foreignEventId, user)
        }
        verify(eventRepository, never()).save(any())
    }
}
