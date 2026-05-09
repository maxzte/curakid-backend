package com.misterio.curakid.reports

import com.misterio.curakid.children.Child
import com.misterio.curakid.children.ChildRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class PeriodService(
    private val periodSummaryRepository: PeriodSummaryRepository
) {
    // Age boundaries in months for developmental periods
    private val PERIOD_BOUNDARIES = listOf(0, 3, 6, 12, 18, 24, 36, 48, 60)

    fun getCurrentPeriodLabel(ageMonths: Long): String {
        val lowerBound = PERIOD_BOUNDARIES.lastOrNull { it <= ageMonths } ?: 0
        val upperBound = PERIOD_BOUNDARIES.firstOrNull { it > ageMonths } ?: 60
        return "$lowerBound-$upperBound months"
    }

    /**
     * Checks if the child has crossed into a new developmental period.
     * If yes, closes the old period and opens a new one.
     * Returns the period summary that was just closed (if any), triggering report generation.
     */
    @Transactional
    fun checkAndClosePeriod(child: Child): PeriodSummary? {
        val ageMonths = ChronoUnit.MONTHS.between(child.birthDate, LocalDate.now())
        val currentLabel = getCurrentPeriodLabel(ageMonths)

        val latestPeriod = periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)

        return if (latestPeriod.isEmpty || latestPeriod.get().periodLabel != currentLabel) {
            // Close the old period if it exists and is open
            val closedPeriod = latestPeriod.orElse(null)?.let {
                if (!it.isClosed) closePeriod(it) else null
            }

            // Open a new period
            val newPeriod = PeriodSummary(
                child = child,
                periodStart = LocalDate.now(),
                periodLabel = currentLabel,
                isClosed = false
            )
            periodSummaryRepository.save(newPeriod)

            closedPeriod
        } else {
            null // Same period, no action needed
        }
    }

    @Transactional
    fun closePeriod(summary: PeriodSummary): PeriodSummary {
        summary.isClosed = true
        summary.periodEnd = LocalDate.now()
        return periodSummaryRepository.save(summary)
    }
}
