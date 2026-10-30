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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
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
class MeasurementServiceSyncTest {

    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var whoZScoreCalculator: WhoZScoreCalculator
    @Mock lateinit var reportProvider: ReportProvider
    @Mock lateinit var transactionManager: PlatformTransactionManager
    private val measurementCalculator = MeasurementCalculator()
    private lateinit var service: MeasurementService

    @BeforeEach
    fun setUp() {
        service = MeasurementService(
            measurementRepository,
            childRepository,
            whoZScoreCalculator,
            reportProvider,
            measurementCalculator,
            transactionManager
        )

        whenever(whoZScoreCalculator.calculateWfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateHfaZscore(any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.calculateBmiZscore(any(), any(), any(), any(), any())).thenReturn(BigDecimal("0.0"))
        whenever(whoZScoreCalculator.zScoreToPercentile(any())).thenReturn(BigDecimal("50.0"))
    }

    @Test
    fun `addMeasurement should merge data for the same timestamp`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val timestamp = Instant.now()

        val existing = Measurement(child = child, measurementDate = timestamp).apply {
            id = UUID.randomUUID()
            weightKg = BigDecimal("8.5")
        }

        whenever(measurementRepository.findById(existing.id!!)).thenReturn(Optional.empty())
        whenever(measurementRepository.findByChildAndMeasurementDate(child, timestamp)).thenReturn(Optional.of(existing))
        whenever(measurementRepository.save(any<Measurement>())).thenAnswer { it.arguments[0] }

        val request = AddMeasurementRequest(
            id = UUID.randomUUID(),
            weightKg = null,
            heightCm = BigDecimal("68.0"),
            headCircCm = null,
            measurementDate = timestamp
        )

        val response = service.addMeasurement(child, request)

        assertEquals(existing.id, response.id)
        assertEquals(BigDecimal("8.5"), response.weightKg)
        assertEquals(BigDecimal("68.0"), response.heightCm)
        verify(measurementRepository).save(any())
    }
}
