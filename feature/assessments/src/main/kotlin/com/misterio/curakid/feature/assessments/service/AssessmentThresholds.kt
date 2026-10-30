package com.misterio.curakid.feature.assessments.service

import java.math.BigDecimal

/**
 * Central place for assessment completion / transition thresholds.
 *
 * Keeping these values in one object makes the rules easy to find and change
 * without touching the evaluation logic itself (Open/Closed Principle).
 */
object AssessmentThresholds {
    const val REQUIRED_COMPLETION_PERCENT = 80
    const val REQUIRED_DOMAIN_COMPLETION_PERCENT = 50
    const val MAX_EARLY_MONTH_GAP = 3
    val EARLY_TRANSITION_Z_THRESHOLD: BigDecimal = BigDecimal("1.50")
}
