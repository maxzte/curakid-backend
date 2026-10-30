package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.assessments.SkillAssessment
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculates the weighted score of a single skill assessment.
 */
@Component
class PeriodScoreCalculator {

    fun calculate(assessment: SkillAssessment): BigDecimal? {
        val latestAnswers = assessment.answers
            .mapNotNull { ans -> ans.question?.id?.let { qId -> qId to ans } }
            .groupBy({ it.first }, { it.second })
            .mapValues { entry -> entry.value.maxBy { it.answeredAt } }
            .values
            .filter { it.selectedOption != null && it.question?.weight != null }

        if (latestAnswers.isEmpty()) return null

        val totalWeight = latestAnswers.sumOf { it.question?.weight ?: BigDecimal.ZERO }
        if (totalWeight <= BigDecimal.ZERO) return null

        val weightedSum = latestAnswers.sumOf {
            val optVal = it.selectedOption?.value?.toBigDecimal() ?: BigDecimal.ZERO
            val weight = it.question?.weight ?: BigDecimal.ZERO
            optVal.multiply(weight)
        }
        return weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP)
    }
}
