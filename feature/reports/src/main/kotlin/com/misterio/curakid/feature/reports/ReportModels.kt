package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.essential.common.analysis.FuzzyRule
import java.math.BigDecimal
import java.util.UUID

data class DomainScores(
    val motorGross: BigDecimal? = null,
    val motorFine: BigDecimal? = null,
    val speech: BigDecimal? = null,
    val cognitive: BigDecimal? = null,
    val social: BigDecimal? = null,
    val selfCare: BigDecimal? = null,
    val sensory: BigDecimal? = null,
    val regressions: Map<String, Boolean> = emptyMap()
)

data class ChildData(
    val gender: Gender,
    val isPremature: Boolean,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val pastIllnesses: List<String>,
    val notes: List<String>,
)

data class MeasurementSummary(
    val wfaZscore: BigDecimal?,
    val hfaZscore: BigDecimal?,
    val bmiZscore: BigDecimal?,
    val hcfaZscore: BigDecimal?,
    val wfhZscore: BigDecimal?,
    val wfaVelocity: BigDecimal? = null,
    val hfaVelocity: BigDecimal? = null,
    val bmiVelocity: BigDecimal? = null,
    val hcfaVelocity: BigDecimal? = null,
    val wfhVelocity: BigDecimal? = null,
    val daysSinceLast: Long? = null
) {
    fun hasVelocity() =
        wfaVelocity != null || hfaVelocity != null || bmiVelocity != null || hcfaVelocity != null || wfhVelocity != null
}

data class GeneratedReport(
    val urgencyScore: BigDecimal,
    val reportText: String,
    val recommendations: String,
    val isFallback: Boolean,
    val faithfulnessScore: Double? = null,
    val relevanceScore: Double? = null
)

data class ReportContext(
    val childId: UUID,
    val ageMonths: Int,
    val ageDays: Int,
    val childData: ChildData,
    val latestMeasurement: MeasurementSummary?,
    val domainScores: DomainScores?,
    val rules: List<FuzzyRule>,
    val periodSummaryId: UUID?,
    val overrideMeasurementId: UUID?
)
