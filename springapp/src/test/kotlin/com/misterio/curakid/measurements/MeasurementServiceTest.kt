package com.misterio.curakid.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import com.misterio.curakid.feature.measurements.AddMeasurementRequest
import com.misterio.curakid.feature.measurements.MeasurementService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

class MeasurementServiceTest {

    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var whoZScoreCalculator: WhoZScoreCalculator
    private lateinit var measurementService: MeasurementService
    private lateinit var reportProvider: ReportProvider

    @BeforeEach
    fun setUp() {
        measurementRepository = mock()
        whoZScoreCalculator = mock()
        reportProvider = mock()
        measurementService =
            MeasurementService(measurementRepository, whoZScoreCalculator, reportProvider)

        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply {
                if (id == null) id = UUID.randomUUID()
            }
        }
    }

    @Test
    fun `addMeasurement should calculate and save all Z-scores`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply {
                id = UUID.randomUUID()
            }
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("10.0"),
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = Instant.now()
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any())).thenReturn(
            BigDecimal(
                "0.5"
            )
        )
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(
            BigDecimal(
                "-0.2"
            )
        )
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(
            BigDecimal("0.1")
        )
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("50.00"))

        val result = measurementService.addMeasurement(child, request)

        assertEquals(BigDecimal("0.5"), result.wfaZscore)
        assertEquals(BigDecimal("-0.2"), result.hfaZscore)
        assertEquals(BigDecimal("0.1"), result.bmiZscore)
        verify(measurementRepository).save(any())
    }

    @Test
    fun `addMeasurement should skip Z-scores if data is missing`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(12)).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(
            weightKg = null,
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = Instant.now()
        )

        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), anyOrNull())).thenReturn(
            BigDecimal("0.0")
        )

        val result = measurementService.addMeasurement(child, request)

        assertNull(result.wfaZscore)
        assertNull(result.bmiZscore)
        assertEquals(BigDecimal("0.0"), result.hfaZscore)
    }

    @Test
    fun `addMeasurement should trigger emergency report if z-score is out of range`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply {
                id = UUID.randomUUID()
            }
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("15.0"), // Extremely high for 12 months
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = Instant.now()
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any())).thenReturn(
            BigDecimal(
                "2.5"
            )
        ) // > 2
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(
            BigDecimal(
                "0.0"
            )
        )
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(
            BigDecimal("0.0")
        )
        whenever(reportProvider.generateEmergencyReport(any(), any())).thenReturn(UUID.randomUUID())

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `addMeasurement should trigger emergency report if delta is high`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply {
                id = UUID.randomUUID()
            }
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("10.0"),
            heightCm = BigDecimal("75.0"),
            headCircCm = null,
            measurementDate = Instant.now()
        )

        // Mock previous measurement for delta calculation
        val prev =
            Measurement(child = child, measurementDate = Instant.now().minus(10, ChronoUnit.DAYS)).apply {
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
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(
            BigDecimal(
                "0.0"
            )
        )
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(
            BigDecimal("0.0")
        )

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `findPreviousScores should use 7 days lookback for newborns`() {
        val child = Child(birthDate = LocalDate.now().minusDays(5)).apply { id = UUID.randomUUID() }
        val now = Instant.now()
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("3.5"),
            heightCm = null,
            headCircCm = null,
            measurementDate = now
        )

        whenever(
            measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
                any(),
                any(),
                any()
            )
        ).thenReturn(emptyList())

        measurementService.addMeasurement(child, request)

        // For age < 14 days, lookback is 7 days
        val expectedLookback = now.minus(7, ChronoUnit.DAYS)
        val expectedTo = now.minus(1, ChronoUnit.DAYS)
        verify(measurementRepository).findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
            eq(child), eq(expectedLookback), eq(expectedTo)
        )
    }
}
