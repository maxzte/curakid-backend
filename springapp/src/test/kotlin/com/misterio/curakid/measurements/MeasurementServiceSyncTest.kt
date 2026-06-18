package com.misterio.curakid.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import com.misterio.curakid.feature.measurements.AddMeasurementRequest
import com.misterio.curakid.feature.measurements.MeasurementService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class MeasurementServiceSyncTest {

    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var whoZScoreCalculator: WhoZScoreCalculator
    private lateinit var reportProvider: ReportProvider
    private lateinit var measurementCalculator: MeasurementCalculator
    private lateinit var service: MeasurementService

    @BeforeEach
    fun setUp() {
        measurementRepository = mock()
        whoZScoreCalculator = mock()
        reportProvider = mock()
        measurementCalculator = MeasurementCalculator()
        service = MeasurementService(
            measurementRepository,
            whoZScoreCalculator,
            reportProvider,
            measurementCalculator
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(
            BigDecimal(
                "0.0"
            )
        )
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(
            BigDecimal(
                "0.0"
            )
        )
        whenever(
            whoZScoreCalculator.calculateBmiZscore(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(
            BigDecimal("0.0")
        )
        whenever(whoZScoreCalculator.zScoreToPercentile(any())).thenReturn(BigDecimal("50.0"))
    }

    @Test
    fun `addMeasurement should merge data for the same timestamp`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6), gender = Gender.MALE).apply {
            id = UUID.randomUUID()
        }
        val timestamp = Instant.now()

        val existing = Measurement(child = child, measurementDate = timestamp).apply {
            id = UUID.randomUUID()
            weightKg = BigDecimal("8.5")
        }

        whenever(measurementRepository.findByChildAndMeasurementDate(eq(child), eq(timestamp)))
            .thenReturn(Optional.of(existing))
        whenever(measurementRepository.save(any<Measurement>())).thenAnswer { it.arguments[0] }
        whenever(measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(any(), any())).thenReturn(
            emptyList()
        )

        val request = AddMeasurementRequest(
            id = UUID.randomUUID(),
            weightKg = null, // don't update weight
            heightCm = BigDecimal("68.0"),
            headCircCm = null,
            measurementDate = timestamp
        )

        val result = service.addMeasurement(child, request)

        assertEquals(BigDecimal("8.5"), existing.weightKg)
        assertEquals(BigDecimal("68.0"), existing.heightCm)
        verify(measurementRepository).save(existing)
    }

    @Test
    fun `addMeasurement should recalculate BMI when weight or height is updated in merged record`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6), gender = Gender.MALE).apply {
            id = UUID.randomUUID()
        }
        val timestamp = Instant.now()

        val existing = Measurement(child = child, measurementDate = timestamp).apply {
            id = UUID.randomUUID()
            weightKg = BigDecimal("8.5")
        }

        whenever(measurementRepository.findByChildAndMeasurementDate(eq(child), eq(timestamp)))
            .thenReturn(Optional.of(existing))
        whenever(measurementRepository.save(any<Measurement>())).thenAnswer { it.arguments[0] }
        whenever(measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(any(), any())).thenReturn(
            emptyList()
        )

        val bmiZ = BigDecimal("1.2")
        whenever(
            whoZScoreCalculator.calculateBmiZscore(
                eq(BigDecimal("8.5")),
                eq(BigDecimal("70.0")),
                any(),
                any(),
                any()
            )
        )
            .thenReturn(bmiZ)

        val request = AddMeasurementRequest(
            id = UUID.randomUUID(),
            weightKg = null,
            heightCm = BigDecimal("70.0"),
            headCircCm = null,
            measurementDate = timestamp
        )

        service.addMeasurement(child, request)

        assertEquals(bmiZ, existing.bmiZscore)
        assertNotNull(existing.bmiPercentile)
    }
}
