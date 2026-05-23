package com.misterio.curakid.event

import com.misterio.curakid.core.essential.common.events.ChildCreatedEvent
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineEvent
import com.misterio.curakid.core.database.event.VaccineEventRepository
import com.misterio.curakid.core.database.event.VaccineTemplateRepository
import com.misterio.curakid.feature.event.VaccineService
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class VaccineServiceTest {

    private lateinit var vaccineEventRepository: VaccineEventRepository
    private lateinit var vaccineTemplateRepository: VaccineTemplateRepository
    private lateinit var childRepository: ChildRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var vaccineService: VaccineService

    @BeforeEach
    fun setUp() {
        vaccineEventRepository = mock()
        vaccineTemplateRepository = mock()
        childRepository = mock()
        eventRepository = mock()
        vaccineService = VaccineService(
            vaccineEventRepository,
            vaccineTemplateRepository,
            childRepository,
            eventRepository
        )
    }

    @Test
    fun `onChildCreated should generate vaccine schedule and doctor visits`() {
        val childId = UUID.randomUUID()
        val birthDate = LocalDate.of(2024, 1, 1)
        val child = Child(birthDate = birthDate).apply { id = childId }

        whenever(childRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(vaccineEventRepository.findAllByChild(child)).thenReturn(emptyList())
        whenever(vaccineTemplateRepository.findAll()).thenReturn(emptyList())
        whenever(vaccineEventRepository.saveAll(any<List<VaccineEvent>>())).thenAnswer { it.arguments[0] }
        whenever(eventRepository.saveAll(any<List<Event>>())).thenAnswer { it.arguments[0] }
        whenever(eventRepository.findAllByChildAndType(eq(child), eq(EventType.DOCTOR))).thenReturn(emptyList())

        vaccineService.onChildCreated(ChildCreatedEvent(childId))

        // Verify doctor visits
        val eventCaptor = argumentCaptor<List<Event>>()
        verify(eventRepository).saveAll(eventCaptor.capture())

        val visits = eventCaptor.firstValue
        Assertions.assertEquals(16, visits.size)

        val firstVisit = visits[0]
        Assertions.assertEquals("Обовʼязковий огляд лікаря", firstVisit.title)
        Assertions.assertEquals(EventType.DOCTOR, firstVisit.type)
        Assertions.assertEquals(birthDate.plusMonths(1).atTime(9, 0), firstVisit.date)

        val secondVisit = visits[1]
        Assertions.assertEquals(birthDate.plusMonths(1).plusDays(15).atTime(9, 0), secondVisit.date)

        val lastVisit = visits.last()
        Assertions.assertEquals(birthDate.plusMonths(72).atTime(9, 0), lastVisit.date)
        Assertions.assertEquals("Плановий профілактичний огляд дитини у віці 6 років", lastVisit.description)

        // Verify vaccines (fallback schedule)
        verify(vaccineEventRepository).saveAll(any<List<VaccineEvent>>())
    }
}