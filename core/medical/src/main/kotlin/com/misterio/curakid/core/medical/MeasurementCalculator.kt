package com.misterio.curakid.core.medical

import com.misterio.curakid.core.database.measurements.Measurement
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class MeasurementCalculator {

    /**
     * Calculates the velocity of a metric change per month (30 days).
     * Formula: (CurrentValue - OldestValue) * 30 / DaysBetween
     */
    fun calculateVelocity(
        history: List<Measurement>,
        currentValue: BigDecimal?,
        currentDate: Instant,
        metric: (Measurement) -> BigDecimal?
    ): BigDecimal? {
        if (currentValue == null) return null
        val points = history.filter { metric(it) != null }
        if (points.isEmpty()) return null

        val oldest = points.last()
        val oldestValue = metric(oldest)!!

        val minutes = ChronoUnit.MINUTES.between(oldest.measurementDate, currentDate)
        // Velocity logic is unstable on micro-intervals (less than 1 hour)
        if (minutes < 60) return null

        // 30 days = 30 * 24 * 60 minutes = 43200 minutes
        val days = minutes.toBigDecimal().divide(BigDecimal("1440"), 4, RoundingMode.HALF_UP)
        val delta = currentValue - oldestValue
        return delta.multiply(BigDecimal("30"))
            .divide(days, 2, RoundingMode.HALF_UP)
    }

    /**
     * Calculates simple delta between current value and the most recent previous value.
     */
    fun calculateDelta(
        history: List<Measurement>,
        currentValue: BigDecimal?,
        metric: (Measurement) -> BigDecimal?
    ): BigDecimal? {
        if (currentValue == null) return null
        val lastPoint = history.firstOrNull { metric(it) != null } ?: return null
        val lastValue = metric(lastPoint) ?: return null
        return (currentValue - lastValue).setScale(2, RoundingMode.HALF_UP)
    }
}
