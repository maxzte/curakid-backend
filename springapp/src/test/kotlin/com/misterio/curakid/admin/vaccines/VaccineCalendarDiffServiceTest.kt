package com.misterio.curakid.admin.vaccines

import com.misterio.curakid.core.database.event.VaccineTemplate
import com.misterio.curakid.core.database.event.VaccineTemplateRepository
import com.misterio.curakid.core.database.event.VaccineType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class VaccineCalendarDiffServiceTest {

    private lateinit var vaccineTemplateRepository: VaccineTemplateRepository
    private lateinit var diffService: VaccineCalendarDiffService

    @BeforeEach
    fun setUp() {
        vaccineTemplateRepository = mock()
        diffService = VaccineCalendarDiffService(vaccineTemplateRepository)
    }

    @Test
    fun `calculateDiff detects new vaccines when not present in repository`() {
        // Arrange
        whenever(vaccineTemplateRepository.findAllByIsActiveTrue()).thenReturn(emptyList())

        val parsed = listOf(
            ParsedVaccine(
                name = "Hepatitis B",
                disease = "HepB",
                doseNumber = 1,
                totalDoses = 3,
                type = VaccineType.VACCINATION,
                scheduledAgeMonths = 0,
                scheduledAgeDays = 1,
                notes = "First 24 hours"
            )
        )

        // Act
        val result = diffService.calculateDiff(parsed)

        // Assert
        assertEquals(1, result.newVaccines.size)
        assertEquals("Hepatitis B", result.newVaccines[0].name)
        assertTrue(result.changedVaccines.isEmpty())
        assertTrue(result.missingVaccines.isEmpty())
    }

    @Test
    fun `calculateDiff detects changed vaccines when schedule or total doses differ`() {
        // Arrange
        val existingId = UUID.randomUUID()
        val existingTemplate = VaccineTemplate(
            name = "Polio",
            disease = "Poliomyelitis",
            doseNumber = 1,
            totalDoses = 4,
            type = VaccineType.VACCINATION,
            scheduledAgeMonths = 2,
            scheduledAgeDays = 60,
            notes = "IPV"
        ).apply { id = existingId }

        whenever(vaccineTemplateRepository.findAllByIsActiveTrue()).thenReturn(listOf(existingTemplate))

        val parsed = listOf(
            ParsedVaccine(
                name = "Polio",
                disease = "Poliomyelitis",
                doseNumber = 1,
                totalDoses = 5, // changed total doses
                type = VaccineType.VACCINATION,
                scheduledAgeMonths = 2,
                scheduledAgeDays = 60,
                notes = "Updated schedule"
            )
        )

        // Act
        val result = diffService.calculateDiff(parsed)

        // Assert
        assertTrue(result.newVaccines.isEmpty())
        assertEquals(1, result.changedVaccines.size)
        assertEquals(existingId, result.changedVaccines[0].existingId)
        assertEquals(4, result.changedVaccines[0].current.totalDoses)
        assertEquals(5, result.changedVaccines[0].proposed.totalDoses)
        assertTrue(result.missingVaccines.isEmpty())
    }

    @Test
    fun `calculateDiff detects missing vaccines when existing template not in parsed list`() {
        // Arrange
        val existingTemplate = VaccineTemplate(
            name = "BCG",
            disease = "Tuberculosis",
            doseNumber = 1,
            totalDoses = 1,
            type = VaccineType.VACCINATION,
            scheduledAgeMonths = 0,
            scheduledAgeDays = 3,
            notes = "At maternity hospital"
        ).apply { id = UUID.randomUUID() }

        whenever(vaccineTemplateRepository.findAllByIsActiveTrue()).thenReturn(listOf(existingTemplate))

        // Act
        val result = diffService.calculateDiff(emptyList())

        // Assert
        assertTrue(result.newVaccines.isEmpty())
        assertTrue(result.changedVaccines.isEmpty())
        assertEquals(1, result.missingVaccines.size)
        assertEquals("BCG", result.missingVaccines[0].name)
    }
}
