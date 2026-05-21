package com.misterio.curakid.reports

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.reports.PeriodSummary
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate
import java.util.*

class PeriodServiceTest {

    private lateinit var periodSummaryRepository: PeriodSummaryRepository
    private lateinit var periodService: PeriodService
    private lateinit var lifePeriodRepository: LifePeriodRepository

    @BeforeEach
    fun setUp() {
        periodSummaryRepository = mock()
        lifePeriodRepository = mock()
        periodService = PeriodService(periodSummaryRepository, lifePeriodRepository)
    }

    @Test
    fun `getCurrentPeriodLabel should return correct buckets`() {
        assertEquals("0-3 months", periodService.getCurrentPeriodLabel(0))
        assertEquals("0-3 months", periodService.getCurrentPeriodLabel(2))
        assertEquals("3-6 months", periodService.getCurrentPeriodLabel(3))
        assertEquals("6-12 months", periodService.getCurrentPeriodLabel(6))
        assertEquals("6-12 months", periodService.getCurrentPeriodLabel(11))
        assertEquals("12-18 months", periodService.getCurrentPeriodLabel(12))
        assertEquals("48-60 months", periodService.getCurrentPeriodLabel(50))
    }

    @Test
    fun `checkAndClosePeriod should open new period if none exists`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(1), gender = Gender.MALE).apply {
            id = UUID.randomUUID()
        }
        val period0to3 = com.misterio.curakid.core.database.development.LifePeriod(code="0M", minMonths=0, maxMonths=3, label="0-3 months")
        whenever(periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)).thenReturn(Optional.empty())
        whenever(lifePeriodRepository.findFirstByAgeMonths(any())).thenReturn(Optional.of(period0to3))

        val result = periodService.checkAndClosePeriod(child)

        assertNull(result) // No closed period
        verify(periodSummaryRepository).save(argThat { lifePeriod?.label == "0-3 months" && !isClosed })
    }

    @Test
    fun `checkAndClosePeriod should close old and open new if boundary crossed`() {
        // Child is 6 months old now
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(6), gender = Gender.MALE).apply {
            id = UUID.randomUUID()
        }

        val oldLifePeriod = com.misterio.curakid.core.database.development.LifePeriod(code="0M", minMonths=0, maxMonths=3, label="0-3 months")
        val oldPeriod = PeriodSummary(child = child, isClosed = false).apply { 
            id = UUID.randomUUID()
            lifePeriod = oldLifePeriod 
        }
        whenever(periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)).thenReturn(Optional.of(oldPeriod))

        val newLifePeriod = com.misterio.curakid.core.database.development.LifePeriod(code="6M", minMonths=6, maxMonths=12, label="6-12 months")
        whenever(lifePeriodRepository.findFirstByAgeMonths(any())).thenReturn(Optional.of(newLifePeriod))

        // Mocking save to return the same object (for simplicity in the test logic check)
        whenever(periodSummaryRepository.save(any())).thenAnswer { it.arguments[0] }

        val closedPeriod = periodService.checkAndClosePeriod(child)

        assertNotNull(closedPeriod)
        assertEquals("0-3 months", closedPeriod?.lifePeriod?.label)
        assertTrue(closedPeriod!!.isClosed)

        // Check that a new one for 6-12 is created
        verify(periodSummaryRepository, times(2)).save(any())
        verify(periodSummaryRepository).save(argThat { lifePeriod?.label == "6-12 months" && !isClosed })
    }

    @Test
    fun `checkAndClosePeriod should do nothing if still in same period`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(1), gender = Gender.MALE).apply {
            id = UUID.randomUUID()
        }
        val currentLifePeriod = com.misterio.curakid.core.database.development.LifePeriod(code="0M", minMonths=0, maxMonths=3, label="0-3 months")
        val currentPeriod = PeriodSummary(child = child, isClosed = false).apply { lifePeriod = currentLifePeriod }

        whenever(periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)).thenReturn(Optional.of(currentPeriod))
        whenever(lifePeriodRepository.findFirstByAgeMonths(any())).thenReturn(Optional.of(currentLifePeriod))

        val result = periodService.checkAndClosePeriod(child)

        assertNull(result)
        verify(periodSummaryRepository, never()).save(any())
    }
}
