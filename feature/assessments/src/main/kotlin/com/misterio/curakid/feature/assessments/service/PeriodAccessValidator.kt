package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import org.springframework.stereotype.Component

/**
 * Validates whether a child is allowed to access/complete a period.
 * Early transition requires an explicit flag, a small age gap and a
 * supportive Z-score signal.
 */
@Component
class PeriodAccessValidator(
    private val measurementRepository: MeasurementRepository
) {

    fun validate(child: Child, min: Int, max: Int, early: Boolean) {
        val correctedAge = child.calculateCorrectedAgeMonths()
        // age in range  → normal access
        // age > max     → child already exceeded this period; completing it is always allowed
        //                 (catch-up scenario), no early-transition checks needed
        if (correctedAge in min..max || correctedAge > max) return

        // age < min — early transition; requires explicit flag and Z-score signal
        if (!early || (min - correctedAge) > AssessmentThresholds.MAX_EARLY_MONTH_GAP) {
            throw IllegalArgumentException("Early transition denied for corrected age $correctedAge")
        }

        val measurement = measurementRepository
            .findFirstByChildOrderByMeasurementDateDesc(child)
            .orElseThrow { IllegalArgumentException("No measurements for early transition") }

        val hasSignal = listOfNotNull(measurement.wfaZscore, measurement.hfaZscore, measurement.bmiZscore)
            .any { it >= AssessmentThresholds.EARLY_TRANSITION_Z_THRESHOLD }

        if (!hasSignal) throw IllegalArgumentException("Low Z-scores for early transition")
    }
}
