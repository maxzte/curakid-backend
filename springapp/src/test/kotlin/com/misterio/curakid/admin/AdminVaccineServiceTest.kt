package com.misterio.curakid.admin

import com.misterio.curakid.admin.service.AdminVaccineService
import com.misterio.curakid.core.database.event.VaccineTemplate
import com.misterio.curakid.core.database.event.VaccineTemplateRepository
import com.misterio.curakid.core.database.event.VaccineType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdminVaccineServiceTest {

    @Mock lateinit var repository: VaccineTemplateRepository
    @Mock lateinit var vaccineCalendarClient: com.misterio.curakid.admin.vaccines.VaccineCalendarClient
    @Mock lateinit var vaccineCalendarParser: com.misterio.curakid.admin.vaccines.VaccineCalendarParser
    @Mock lateinit var vaccineCalendarDiffService: com.misterio.curakid.admin.vaccines.VaccineCalendarDiffService
    @Mock lateinit var childRepository: com.misterio.curakid.core.database.children.ChildRepository
    @Mock lateinit var vaccineEventRepository: com.misterio.curakid.core.database.event.VaccineEventRepository
    @Mock lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher
    private lateinit var service: AdminVaccineService

    @BeforeEach
    fun setUp() {
        service = AdminVaccineService(
            repository,
            vaccineCalendarClient,
            vaccineCalendarParser,
            vaccineCalendarDiffService,
            childRepository,
            vaccineEventRepository,
            eventPublisher
        )
    }

    @Test
    fun `getTemplates should return active templates`() {
        val templates = listOf(
            VaccineTemplate(name = "BCG", type = VaccineType.VACCINATION),
            VaccineTemplate(name = "Polio", type = VaccineType.VACCINATION)
        )
        whenever(repository.findAllByIsActiveTrue()).thenReturn(templates)

        val result = service.getTemplates()

        assertEquals(2, result.size)
        assertEquals("BCG", result[0].name)
        assertEquals("Polio", result[1].name)
    }

    @Test
    fun `createTemplate should save and return new template`() {
        val request = CreateVaccineTemplateRequest(
            name = "BCG", disease = "Tuberculosis", doseNumber = 1, totalDoses = 1,
            type = "VACCINATION", scheduledAgeMonths = 0, scheduledAgeDays = 3, notes = null
        )
        whenever(repository.save(any<VaccineTemplate>())).thenAnswer { it.arguments[0] }

        val result = service.createTemplate(request)

        assertEquals("BCG", result.name)
        assertEquals("VACCINATION", result.type)
        verify(repository).save(any<VaccineTemplate>())
    }

    @Test
    fun `updateTemplate should update and return existing template`() {
        val id = UUID.randomUUID()
        val existing = VaccineTemplate(name = "Old", type = VaccineType.VACCINATION).apply { this.setId(id) }
        val request = CreateVaccineTemplateRequest(
            name = "BCG Updated", disease = "Tuberculosis", doseNumber = 1, totalDoses = 1,
            type = "VACCINATION", scheduledAgeMonths = 0, scheduledAgeDays = 3, notes = null
        )
        whenever(repository.findById(id)).thenReturn(Optional.of(existing))
        whenever(repository.save(any<VaccineTemplate>())).thenAnswer { it.arguments[0] }

        val result = service.updateTemplate(id, request)

        assertEquals("BCG Updated", result.name)
        assertEquals("BCG Updated", existing.name)
    }

    @Test
    fun `updateTemplate should throw when template not found`() {
        val id = UUID.randomUUID()
        whenever(repository.findById(id)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            service.updateTemplate(id, CreateVaccineTemplateRequest(
                name = "Test", disease = "", type = "VACCINATION",
                doseNumber = null, totalDoses = null, scheduledAgeMonths = null, scheduledAgeDays = null, notes = null
            ))
        }
    }

    @Test
    fun `deleteTemplate should deactivate template`() {
        val id = UUID.randomUUID()
        val template = VaccineTemplate(name = "BCG", type = VaccineType.VACCINATION).apply { this.setId(id) }
        whenever(repository.findById(id)).thenReturn(Optional.of(template))
        whenever(repository.save(any<VaccineTemplate>())).thenAnswer { it.arguments[0] }

        service.deleteTemplate(id)

        assertFalse(template.isActive)
        verify(repository).save(template)
    }

    @Test
    fun `deleteTemplate should throw when template not found`() {
        val id = UUID.randomUUID()
        whenever(repository.findById(id)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            service.deleteTemplate(id)
        }
    }

    @Test
    fun `backfillForExistingChildren only queries active children and creates events`() {
        val user = com.misterio.curakid.core.database.auth.User(
            email = "user@test.com", passwordHash = "hash", name = "User"
        )
        val activeChild = com.misterio.curakid.core.database.children.Child(
            user = user,
            name = "Baby",
            birthDate = java.time.LocalDate.now().minusMonths(2),
            gender = com.misterio.curakid.core.database.children.Gender.FEMALE
        ).apply { id = UUID.randomUUID() }

        val template = VaccineTemplate(
            name = "Polio",
            disease = "Polio",
            doseNumber = 1,
            totalDoses = 3,
            type = VaccineType.VACCINATION,
            scheduledAgeMonths = 2,
            scheduledAgeDays = 60
        ).apply { id = UUID.randomUUID() }

        whenever(childRepository.findAllByIsActiveTrue(org.mockito.kotlin.any())).thenReturn(
            org.springframework.data.domain.PageImpl(listOf(activeChild))
        )
        whenever(repository.findAllByIsActiveTrue()).thenReturn(listOf(template))
        whenever(vaccineEventRepository.findAllByChild(activeChild)).thenReturn(emptyList())

        val summary = service.backfillForExistingChildren()

        assertEquals(1, summary.totalChildren)
        assertEquals(1, summary.createdEventsCount)
        verify(childRepository).findAllByIsActiveTrue(org.mockito.kotlin.any())
        verify(vaccineEventRepository).saveAll(any<List<com.misterio.curakid.core.database.event.VaccineEvent>>())
    }
}
