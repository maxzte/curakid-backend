package com.misterio.curakid.fis

import java.math.BigDecimal

/**
 * Request sent to Python FIS service.
 * Contains child measurements, skill scores, and the fuzzy rules to evaluate.
 */
data class FisRequest(
    val childId: String,
    val ageMonths: Int,
    val gender: String?,

    // Anthropometric Z-scores
    val wfaZscore: BigDecimal?,
    val hfaZscore: BigDecimal?,
    val bmiZscore: BigDecimal?,

    // Skill assessment scores (0-100) per domain
    val motorScore: BigDecimal?,
    val speechScore: BigDecimal?,
    val cognitiveScore: BigDecimal?,
    val socialScore: BigDecimal?,

    // Fuzzy rules loaded from DB and passed to FIS at runtime
    val rules: List<FuzzyRule>
)

data class FuzzyRule(
    val domain: String,             // e.g. "weight", "motor"
    val conditions: List<FuzzyCondition>,
    val consequent: String          // e.g. "high_urgency"
)

data class FuzzyCondition(
    val variable: String,           // e.g. "wfa_zscore"
    val term: String                // e.g. "very_low"
)

/**
 * Response from Python FIS service.
 */
data class FisResponse(
    val urgencyScore: BigDecimal,   // 0.0 – 1.0
    val isFallback: Boolean = false,
    val breakdown: Map<String, BigDecimal> = emptyMap() // per-domain scores
)
