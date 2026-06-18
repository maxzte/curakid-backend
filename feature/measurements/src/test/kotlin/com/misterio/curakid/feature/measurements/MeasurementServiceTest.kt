package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MeasurementServiceTest {

    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var whoZScoreCalculator: WhoZScoreCalculator
    @Mock lateinit var reportProvider: ReportProvider
    private val measurementCalculator = MeasurementCalculator()
    private lateinit var measurementService: MeasurementService

    @BeforeEach
    fun setUp() {
        measurementService = MeasurementService(measurementRepository, whoZScoreCalculator, reportProvider, measurementCalculator)

        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply { if (id == null) id = UUID.randomUUID() }
        }
    }

    @Test
    fun `addMeasurement should calculate and save all Z-scores`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("10.0"), heightCm = BigDecimal("75.0"), headCircCm = null, measurementDate = Instant.now())

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-0.2"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.1"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("50.00"))

        val result = measurementService.addMeasurement(child, request)

        assertEquals(BigDecimal("0.5"), result.wfaZscore)
        assertEquals(BigDecimal("-0.2"), result.hfaZscore)
        assertEquals(BigDecimal("0.1"), result.bmiZscore)
        verify(measurementRepository).save(any())
    }

    @Test
    fun `addMeasurement should skip Z-scores if data is missing`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(12)).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = null, heightCm = BigDecimal("75.0"), headCircCm = null, measurementDate = Instant.now())

        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), anyOrNull(), any())).thenReturn(BigDecimal("0.0"))

        val result = measurementService.addMeasurement(child, request)

        assertNull(result.wfaZscore)
        assertNull(result.bmiZscore)
        assertEquals(BigDecimal("0.0"), result.hfaZscore)
    }

    @Test
    fun `addMeasurement should trigger emergency report if z-score is out of range`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("15.0"), heightCm = BigDecimal("75.0"), headCircCm = null, measurementDate = Instant.now())

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("2.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(reportProvider.generateEmergencyReport(any(), any())).thenReturn(UUID.randomUUID())

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `addMeasurement should trigger emergency report if delta is high`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("10.0"), heightCm = BigDecimal("75.0"), headCircCm = null, measurementDate = Instant.now())

        val prev = Measurement(child = child, measurementDate = Instant.now().minus(10, ChronoUnit.DAYS)).apply { wfaZscore = BigDecimal("0.5") }
        whenever(measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(any(), any(), any())).thenReturn(listOf(prev))

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("1.6"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `findPreviousScores should use 7 days lookback for newborns`() {
        val child = Child(birthDate = LocalDate.now().minusDays(5)).apply { id = UUID.randomUUID() }
        val now = Instant.now()
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("3.5"), heightCm = null, headCircCm = null, measurementDate = now)

        whenever(measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(any(), any(), any())).thenReturn(emptyList())

        measurementService.addMeasurement(child, request)

        val expectedLookback = now.minus(7, ChronoUnit.DAYS)
        val expectedTo = now.minus(1, ChronoUnit.MILLIS)
        verify(measurementRepository).findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(eq(child), eq(expectedLookback), eq(expectedTo))
    }

    @Test
    fun `addMeasurement - Scenario 1 - Normal`() {
        val child = Child(birthDate = LocalDate.parse("2026-04-27"), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("4.2"), heightCm = BigDecimal("54.0"), headCircCm = BigDecimal("37.0"), measurementDate = Instant.parse("2026-05-27T10:00:00Z"))

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-0.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.2"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateHcfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.1"))
        whenever(whoZScoreCalculator.calculateWfhZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("30.0"))

        val result = measurementService.addMeasurement(child, request)

        assertNull(result.aiReportId)
        verify(reportProvider, never()).generateEmergencyReport(any(), any())
    }

    @Test
    fun `addMeasurement - Scenario 2 - Below Normal`() {
        val child = Child(birthDate = LocalDate.parse("2026-04-27"), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("2.8"), heightCm = BigDecimal("50.0"), headCircCm = BigDecimal("34.0"), measurementDate = Instant.parse("2026-05-27T10:00:00Z"))

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-2.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-1.5"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("-2.1"))
        whenever(whoZScoreCalculator.calculateHcfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-1.2"))
        whenever(whoZScoreCalculator.calculateWfhZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("-2.0"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("1.0"))

        val reportId = UUID.randomUUID()
        whenever(reportProvider.generateEmergencyReport(eq(child.id!!), any())).thenReturn(reportId)

        val result = measurementService.addMeasurement(child, request)

        assertEquals(reportId, result.aiReportId)
        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `addMeasurement - Scenario 3 - Critical`() {
        val child = Child(birthDate = LocalDate.parse("2026-04-27"), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("2.3"), heightCm = BigDecimal("47.0"), headCircCm = BigDecimal("32.0"), measurementDate = Instant.parse("2026-05-27T10:00:00Z"))

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-3.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-2.5"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("-3.1"))
        whenever(whoZScoreCalculator.calculateHcfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-2.2"))
        whenever(whoZScoreCalculator.calculateWfhZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("-3.0"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("0.1"))

        val reportId = UUID.randomUUID()
        whenever(reportProvider.generateEmergencyReport(eq(child.id!!), any())).thenReturn(reportId)

        val result = measurementService.addMeasurement(child, request)

        assertEquals(reportId, result.aiReportId)
        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }
}
