package com.misterio.curakid.feature.fis

import com.misterio.curakid.common.analysis.FuzzyRule
import java.math.BigDecimal

data class FisRequest(
    val childId: String,
    val ageMonths: Int,
    val gender: String?,
    val wfaZscore: BigDecimal?,
    val hfaZscore: BigDecimal?,
    val bmiZscore: BigDecimal?,
    val hcfaZscore: BigDecimal?,
    val wfhZscore: BigDecimal?,
    val motorGrossScore: BigDecimal?,
    val motorFineScore: BigDecimal?,
    val speechScore: BigDecimal?,
    val cognitiveScore: BigDecimal?,
    val socialScore: BigDecimal?,
    val selfCareScore: BigDecimal?,
    val sensoryScore: BigDecimal?,
    val rules: List<FuzzyRule>
)

data class FisResponse(
    val urgencyScore: BigDecimal,
    val isFallback: Boolean = false,
    val breakdown: Map<String, BigDecimal> = emptyMap()
)
