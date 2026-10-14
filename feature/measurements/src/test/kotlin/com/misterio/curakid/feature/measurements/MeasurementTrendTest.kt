package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MeasurementTrendTest {

    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var whoZScoreCalculator: WhoZScoreCalculator
    @Mock lateinit var reportProvider: ReportProvider
    private val measurementCalculator = MeasurementCalculator()
    private lateinit var measurementService: MeasurementService

    @BeforeEach
    fun setUp() {
        measurementService = MeasurementService(measurementRepository, childRepository, whoZScoreCalculator, reportProvider, measurementCalculator)

        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply { if (id == null) id = UUID.randomUUID() }
        }
    }

    @Test
    fun `should detect persistent decline (velocity trigger)`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val now = Instant.now()

        val h1 = Measurement(child = child, measurementDate = now.minus(60, ChronoUnit.DAYS)).apply {
            id = UUID.randomUUID()
            wfaZscore = BigDecimal("0.5")
        }
        whenever(measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(eq(child), any())).thenReturn(listOf(h1))
        whenever(measurementRepository.findByChildAndMeasurementDate(any(), any())).thenReturn(Optional.empty())

        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("7.0"), heightCm = BigDecimal("65.0"), headCircCm = null, measurementDate = now)

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("-0.5"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateWfhZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("50.0"))

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }

    @Test
    fun `should detect potential outlier (high delta trigger)`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val now = Instant.now()

        val prev = Measurement(child = child, measurementDate = now.minus(10, ChronoUnit.DAYS)).apply { wfaZscore = BigDecimal("0.0") }
        whenever(measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(any(), any(), any())).thenReturn(listOf(prev))
        whenever(measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(eq(child), any())).thenReturn(emptyList())
        whenever(measurementRepository.findByChildAndMeasurementDate(any(), any())).thenReturn(Optional.empty())

        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("10.0"), heightCm = null, headCircCm = null, measurementDate = now)

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("2.6"))
        whenever(whoZScoreCalculator.zScoreToPercentile(anyOrNull())).thenReturn(BigDecimal("99.0"))

        measurementService.addMeasurement(child, request)

        verify(reportProvider).generateEmergencyReport(eq(child.id!!), any())
    }
}
