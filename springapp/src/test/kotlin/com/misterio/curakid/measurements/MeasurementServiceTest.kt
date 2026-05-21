package com.misterio.curakid.measurements

import com.misterio.curakid.common.analysis.ReportProvider
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.feature.measurements.AddMeasurementRequest
import com.misterio.curakid.feature.measurements.MeasurementService
import com.misterio.curakid.feature.measurements.WhoPercentileCalculator
import com.misterio.curakid.feature.measurements.WhoZScoreCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    private lateinit var whoPercentileCalculator: WhoPercentileCalculator
    private lateinit var measurementService: MeasurementService
    private lateinit var reportProvider: ReportProvider

    @BeforeEach
    fun setUp() {
        measurementRepository = mock()
        whoZScoreCalculator = mock()
        whoPercentileCalculator = mock()
        reportProvider = mock()
        measurementService =
            MeasurementService(measurementRepository, whoZScoreCalculator, whoPercentileCalculator, reportProvider)
    }

    @Test
    fun `addMeasurement should calculate and save all Z-scores`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("10.0"),
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = LocalDateTime.now()
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any())).thenReturn(BigDecimal("0.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(BigDecimal("-0.2"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.1"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("50.00"))
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

    @Test
    fun `addMeasurement should trigger emergency report if z-score is out of range`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("15.0"), // Extremely high for 12 months
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = LocalDateTime.now()
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any())).thenReturn(BigDecimal("2.5")) // > 2
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply { id = UUID.randomUUID() }
        }
        whenever(reportProvider.generateEmergencyReport(any(), any())).thenReturn(UUID.randomUUID())

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `addMeasurement should trigger emergency report if delta is high`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("10.0"),
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = LocalDateTime.now()
        )

        // Mock previous measurement for delta calculation
        val prev = Measurement(child = child, measurementDate = LocalDateTime.now().minusDays(10)).apply {
            wfaZscore = BigDecimal("0.5")
        }
        whenever(
            measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
                any(),
                any(),
                any()
            )
        )
            .thenReturn(listOf(prev))

        whenever(
            whoZScoreCalculator.calculateWfaZscore(
                any(),
                any(),
                any()
            )
        ).thenReturn(BigDecimal("1.6")) // Delta 1.1 > 1.0
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply { id = UUID.randomUUID() }
        }

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `findPreviousScores should use 7 days lookback for newborns`() {
        val child = Child(birthDate = LocalDate.now().minusDays(5)).apply { id = UUID.randomUUID() }
        val now = LocalDateTime.now()
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("3.5"),
            heightCm = null,
            headCircCm = null,
            measurementDate = now
        )

        whenever(measurementRepository.save(any<Measurement>())).thenAnswer { it.arguments[0] }
        whenever(measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(any(), any(), any())).thenReturn(emptyList())

        measurementService.addMeasurement(child, request)

        // For age < 14 days, lookback is 7 days
        val expectedLookback = now.minusDays(7)
        verify(measurementRepository).findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
            eq(child), eq(expectedLookback), any()
        )
    }
}
