package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.feature.assessments.DomainReadinessResponse
import com.misterio.curakid.feature.assessments.PeriodReadinessResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Evaluates whether a life period has enough data to be completed.
 * This includes overall progress, per-domain coverage, the previous period
 * gate and age/early-transition rules.
 */
@Component
class PeriodReadinessEvaluator(
    private val lifePeriodRepository: LifePeriodRepository,
    private val assessmentRepository: SkillAssessmentRepository,
    private val periodSummaryRepository: PeriodSummaryRepository,
    private val periodAccessValidator: PeriodAccessValidator,
    private val registrationAgeCalculator: RegistrationAgeCalculator
) {

    @Transactional(readOnly = true)
    @Cacheable(value = ["assessmentReadiness"], key = "#child.id.toString() + '_' + #periodCode")
    fun getReadiness(child: Child, periodCode: String): PeriodReadinessResponse {
        val period = lifePeriodRepository.findByCodeWithQuestions(periodCode)
            .orElseThrow { IllegalArgumentException("Period not found") }

        return evaluate(child, period)
    }

    fun evaluate(child: Child, period: LifePeriod): PeriodReadinessResponse {
        val correctedAge = child.calculateCorrectedAgeMonths()
        val isAgeAppropriate = correctedAge >= period.minMonths

        val periodQuestionIds = period.questions.mapNotNull { it.id }.toSet()
        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)

        val answeredQuestionIds = assessments.flatMap { it.answers }
            .mapNotNull { it.question?.id }
            .filter { it in periodQuestionIds }
            .toSet()

        val totalQuestions = period.questions.size
        if (totalQuestions == 0) {
            return PeriodReadinessResponse(
                false,
                0,
                AssessmentThresholds.REQUIRED_COMPLETION_PERCENT,
                emptyList()
            )
        }

        val totalCompletionPercent = (answeredQuestionIds.size * 100) / totalQuestions

        // Domain-specific status
        val questionsByDomain = period.questions.groupBy { it.domain }
        val domainStatus = questionsByDomain.map { (domain, questions) ->
            val domainQuestionIds = questions.mapNotNull { it.id }.toSet()
            val domainAnsweredCount = answeredQuestionIds.count { it in domainQuestionIds }
            val domainPercent = if (questions.isNotEmpty()) (domainAnsweredCount * 100) / questions.size else 100
            DomainReadinessResponse(
                domain = domain.name,
                completionPercent = domainPercent,
                isReady = domainPercent >= AssessmentThresholds.REQUIRED_DOMAIN_COMPLETION_PERCENT,
                requiredPercent = AssessmentThresholds.REQUIRED_DOMAIN_COMPLETION_PERCENT
            )
        }

        val reasons = mutableListOf<String>()
        if (totalCompletionPercent < AssessmentThresholds.REQUIRED_COMPLETION_PERCENT) {
            reasons.add("Загальний прогрес: $totalCompletionPercent% < ${AssessmentThresholds.REQUIRED_COMPLETION_PERCENT}%")
        }
        domainStatus.filter { !it.isReady }.forEach {
            reasons.add("Недостатньо даних у розділі ${it.domain}: ${it.completionPercent}% < ${AssessmentThresholds.REQUIRED_DOMAIN_COMPLETION_PERCENT}%")
        }

        // Check if preceding active period is completed
        val registrationAge = registrationAgeCalculator.calculate(child)
        val allPeriods = lifePeriodRepository.findAllWithQuestions().sortedBy { it.minMonths }
        val initialPeriod = allPeriods.firstOrNull { registrationAge >= it.minMonths && registrationAge <= it.maxMonths }
            ?: allPeriods.firstOrNull()

        val isHistorical = initialPeriod != null && period.minMonths < initialPeriod.minMonths
        var previousPeriodCompleted = true
        if (!isHistorical && initialPeriod != null) {
            val precedingActivePeriod = allPeriods
                .filter { it.minMonths >= initialPeriod.minMonths && it.maxMonths <= period.minMonths && it.id != period.id }
                .maxByOrNull { it.minMonths }

            if (precedingActivePeriod != null) {
                previousPeriodCompleted = periodSummaryRepository.findByChildAndLifePeriod(child, precedingActivePeriod)
                    .map { it.isClosed }
                    .orElse(false)

                if (!previousPeriodCompleted) {
                    reasons.add("Попередній період '${precedingActivePeriod.label}' ще не завершено. Будь ласка, завершіть його перед цим етапом.")
                }
            }
        }

        var canTransitionEarly = false
        if (!isAgeAppropriate) {
            try {
                periodAccessValidator.validate(child, period.minMonths, period.maxMonths, true)
                canTransitionEarly = true
            } catch (e: Exception) {
                reasons.add("Дитина ще занадто мала для цього періоду (потрібно від ${period.minMonths} міс., коригований вік $correctedAge міс.)")
            }
        }

        val isReady = totalCompletionPercent >= AssessmentThresholds.REQUIRED_COMPLETION_PERCENT &&
                domainStatus.all { it.isReady } &&
                previousPeriodCompleted &&
                (isAgeAppropriate || canTransitionEarly)

        return PeriodReadinessResponse(
            isReady = isReady,
            completionPercent = totalCompletionPercent,
            requiredPercent = AssessmentThresholds.REQUIRED_COMPLETION_PERCENT,
            domainStatus = domainStatus,
            reasons = reasons,
            canTransitionEarly = canTransitionEarly
        )
    }
}
