package com.misterio.curakid.measurements

import com.misterio.curakid.children.Child
import com.misterio.curakid.children.Gender
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class MeasurementServiceTest {

    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var whoZScoreCalculator: WhoZScoreCalculator
    private lateinit var measurementService: MeasurementService

    @BeforeEach
    fun setUp() {
        measurementRepository = mock()
        whoZScoreCalculator = mock()
        measurementService = MeasurementService(measurementRepository, whoZScoreCalculator)
    }

    @Test
    fun `addMeasurement should calculate and save all Z-scores`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("10.0"),
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = LocalDateTime.now()
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any())).thenReturn(BigDecimal("0.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(BigDecimal("-0.2"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.1"))
        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply { id = UUID.randomUUID() }
        }

        val result = measurementService.addMeasurement(child, request)

        assertEquals(BigDecimal("0.5"), result.wfaZscore)
        assertEquals(BigDecimal("-0.2"), result.hfaZscore)
        assertEquals(BigDecimal("0.1"), result.bmiZscore)
        verify(measurementRepository).save(any())
    }

    @Test
    fun `addMeasurement should skip Z-scores if data is missing`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(12)).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(
            weightKg = null,
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = LocalDateTime.now()
        )

        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), anyOrNull())).thenReturn(BigDecimal("0.0"))
        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply { id = UUID.randomUUID() }
        }

        val result = measurementService.addMeasurement(child, request)

        assertNull(result.wfaZscore)
        assertNull(result.bmiZscore)
        assertEquals(BigDecimal("0.0"), result.hfaZscore)
    }
}
