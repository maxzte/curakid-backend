package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
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
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeasurementServiceTest {

    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var whoZScoreCalculator: WhoZScoreCalculator
    @Mock lateinit var reportProvider: ReportProvider
    @Mock lateinit var transactionManager: PlatformTransactionManager
    private val measurementCalculator = MeasurementCalculator()
    private lateinit var measurementService: MeasurementService

    @BeforeEach
    fun setUp() {
        measurementService = MeasurementService(
            measurementRepository,
            childRepository,
            whoZScoreCalculator,
            reportProvider,
            measurementCalculator,
            transactionManager
        )

        whenever(measurementRepository.save(any<Measurement>())).thenAnswer {
            (it.arguments[0] as Measurement).apply { if (id == null) id = UUID.randomUUID() }
        }
    }

    @Test
    fun `addMeasurement should calculate and save all Z-scores`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val request = AddMeasurementRequest(id = UUID.randomUUID(), weightKg = BigDecimal("10.0"), heightCm = BigDecimal("75.0"), headCircCm = null, measurementDate = Instant.now())

        whenever(measurementRepository.findById(request.id)).thenReturn(Optional.empty())
        whenever(measurementRepository.findByChildAndMeasurementDate(child, request.measurementDate)).thenReturn(Optional.empty())
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

        whenever(measurementRepository.findById(request.id)).thenReturn(Optional.empty())
        whenever(measurementRepository.findByChildAndMeasurementDate(child, request.measurementDate)).thenReturn(Optional.empty())
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), anyOrNull(), any())).thenReturn(BigDecimal("0.0"))

        val result = measurementService.addMeasurement(child, request)

        assertNull(result.wfaZscore)
        assertNull(result.bmiZscore)
        assertEquals(BigDecimal("0.0"), result.hfaZscore)
    }
}
