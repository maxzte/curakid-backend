package com.misterio.curakid.reports

import com.misterio.curakid.children.Child
import com.misterio.curakid.children.Gender
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.LocalDate
import java.util.*

class PeriodServiceTest {

    private lateinit var periodSummaryRepository: PeriodSummaryRepository
    private lateinit var periodService: PeriodService

    @BeforeEach
    fun setUp() {
        periodSummaryRepository = mock()
        periodService = PeriodService(periodSummaryRepository)
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
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(1), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        whenever(periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)).thenReturn(Optional.empty())

        val result = periodService.checkAndClosePeriod(child)

        assertNull(result) // No closed period
        verify(periodSummaryRepository).save(argThat { periodLabel == "0-3 months" && !isClosed })
    }

    @Test
    fun `checkAndClosePeriod should close old and open new if boundary crossed`() {
        // Child is 6 months old now
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(6), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        
        // Previous record was 0-3 months (stale)
        val oldPeriod = PeriodSummary(child = child, periodLabel = "0-3 months", isClosed = false).apply { id = UUID.randomUUID() }
        whenever(periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)).thenReturn(Optional.of(oldPeriod))
        
        // Mocking save to return the same object (for simplicity in the test logic check)
        whenever(periodSummaryRepository.save(any())).thenAnswer { it.arguments[0] }

        val closedPeriod = periodService.checkAndClosePeriod(child)

        assertNotNull(closedPeriod)
        assertEquals("0-3 months", closedPeriod?.periodLabel)
        assertTrue(closedPeriod!!.isClosed)
        
        // Check that a new one for 6-12 is created
        verify(periodSummaryRepository, times(2)).save(any())
        verify(periodSummaryRepository).save(argThat { periodLabel == "6-12 months" && !isClosed })
    }

    @Test
    fun `checkAndClosePeriod should do nothing if still in same period`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(1), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val currentPeriod = PeriodSummary(child = child, periodLabel = "0-3 months", isClosed = false)
        
        whenever(periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)).thenReturn(Optional.of(currentPeriod))

        val result = periodService.checkAndClosePeriod(child)

        assertNull(result)
        verify(periodSummaryRepository, never()).save(any())
    }
}
