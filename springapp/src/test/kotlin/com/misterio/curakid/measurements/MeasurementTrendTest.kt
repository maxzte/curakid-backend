package com.misterio.curakid.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import com.misterio.curakid.feature.measurements.AddMeasurementRequest
import com.misterio.curakid.feature.measurements.MeasurementService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

class MeasurementTrendTest {

    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var whoZScoreCalculator: WhoZScoreCalculator
    private lateinit var reportProvider: ReportProvider
    private lateinit var measurementService: MeasurementService

    @BeforeEach
    fun setUp() {
        measurementRepository = mock()
        whoZScoreCalculator = mock()
        reportProvider = mock()
        measurementService = MeasurementService(measurementRepository, whoZScoreCalculator, reportProvider)

        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply {
                if (id == null) id = UUID.randomUUID()
            }
        }
    }

    @Test
    fun `should detect persistent decline (velocity trigger)`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val now = Instant.now()
        
        // History: Z-score was 0.5 two months ago
        val h1 = Measurement(child = child, measurementDate = now.minus(60, ChronoUnit.DAYS)).apply {
            wfaZscore = BigDecimal("0.5")
        }
        whenever(measurementRepository.findTop5ByChildOrderByMeasurementDateDesc(child)).thenReturn(listOf(h1))

        // Current: Z-score is -0.5
        // Change = -1.0 over 60 days. Velocity = (-1.0 * 30) / 60 = -0.5 per month.
        // Threshold is -0.3. This should trigger WFA_PERSISTENT_DECLINE.
        
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("7.0"),
            heightCm = BigDecimal("65.0"),
            headCircCm = null,
            measurementDate = now
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any())).thenReturn(BigDecimal("-0.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateWfhZscore(any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("50.0"))

        measurementService.addMeasurement(child, request)

        // Verify that report was triggered (due to WFA_PERSISTENT_DECLINE)
        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `should detect potential outlier (high delta trigger)`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val now = Instant.now()

        // Mock previous measurement for delta calculation
        val prev = Measurement(child = child, measurementDate = now.minus(10, ChronoUnit.DAYS)).apply {
            wfaZscore = BigDecimal("0.0")
        }
        whenever(measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(any(), any(), any()))
            .thenReturn(listOf(prev))
        whenever(measurementRepository.findTop5ByChildOrderByMeasurementDateDesc(child)).thenReturn(emptyList())

        // Current Z-score is 2.6 (Delta = 2.6 > 2.5 threshold for outlier)
        val request = AddMeasurementRequest(
            weightKg = BigDecimal("10.0"),
            heightCm = null,
            headCircCm = null,
            measurementDate = now
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any())).thenReturn(BigDecimal("2.6"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("99.0"))

        measurementService.addMeasurement(child, request)

        // Verify report triggered (POTENTIAL_OUTLIER is one of the triggers)
        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }
}
