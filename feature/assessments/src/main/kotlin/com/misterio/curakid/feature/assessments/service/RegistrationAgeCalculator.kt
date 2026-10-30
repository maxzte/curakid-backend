package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.children.Child
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Calculates the age (in months) a child had when they were registered.
 * Premature babies are corrected by subtracting the number of months they
 * were born early (only while younger than 24 months).
 */
@Component
class RegistrationAgeCalculator {

    fun calculate(child: Child): Int {
        val createdInstant = child.createdAt ?: Instant.now()
        val createdDate = createdInstant.atZone(ZoneOffset.UTC).toLocalDate()
        val chronologicalAge = ChronoUnit.MONTHS.between(child.birthDate, createdDate).toInt()
        val weeks = child.gestationalWeeks
        if (!child.isPremature || weeks == null || weeks >= 40 || chronologicalAge >= 24) {
            return chronologicalAge.coerceAtLeast(0)
        }
        val weeksEarly = 40 - weeks
        val monthsEarly = weeksEarly / 4
        return (chronologicalAge - monthsEarly).coerceAtLeast(0)
    }
}
