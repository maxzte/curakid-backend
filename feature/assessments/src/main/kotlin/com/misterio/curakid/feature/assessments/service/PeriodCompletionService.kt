package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.reports.PeriodSummary
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.feature.assessments.AssessmentResponse
import com.misterio.curakid.feature.assessments.CompletePeriodAssessmentRequest
import com.misterio.curakid.feature.assessments.CompletePeriodAssessmentResponse
import com.misterio.curakid.feature.assessments.toAssessmentResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.writeTx
import java.time.Instant
import java.time.LocalDate

/**
 * Orchestrates closing a life period: validates readiness, recalculates
 * scores, marks the period summary as closed and triggers report generation.
 */
@Component
class PeriodCompletionService(
    private val assessmentRepository: SkillAssessmentRepository,
    private val lifePeriodRepository: LifePeriodRepository,
    private val periodSummaryRepository: PeriodSummaryRepository,
    private val reportProvider: ReportProvider,
    private val transactionManager: PlatformTransactionManager,
    private val periodReadinessEvaluator: PeriodReadinessEvaluator,
    private val periodScoreCalculator: PeriodScoreCalculator,
    private val readinessCacheEvictor: ReadinessCacheEvictor
) {

    fun complete(
        child: Child,
        request: CompletePeriodAssessmentRequest
    ): CompletePeriodAssessmentResponse {
        readinessCacheEvictor.evict(child.id, request.periodCode)
        val transactionResult = requireNotNull(transactionManager.writeTx {
            completeInTransaction(child, request)
        }) { "Period assessment transaction failed or was rolled back" }

        val childId = requireNotNull(child.id) { "Child ID must not be null" }
        val summaryId = requireNotNull(transactionResult.summary.id) { "Summary ID must not be null" }
        val reportId = reportProvider.generatePeriodReport(childId, summaryId)

        return CompletePeriodAssessmentResponse(
            periodCode = request.periodCode,
            completionPercent = transactionResult.completionPercent,
            requiredPercent = AssessmentThresholds.REQUIRED_COMPLETION_PERCENT,
            completedAt = transactionResult.completedAt,
            assessments = transactionResult.updatedAssessments,
            reportId = reportId
        )
    }

    private fun completeInTransaction(
        child: Child,
        request: CompletePeriodAssessmentRequest
    ): CompletePeriodAssessmentResult {
        val period = lifePeriodRepository.findByCodeWithQuestions(request.periodCode)
            .orElseThrow { IllegalArgumentException("Period not found") }

        val readiness = periodReadinessEvaluator.evaluate(child, period)

        if (!readiness.isReady) {
            if (readiness.canTransitionEarly && request.allowEarlyTransition) {
                // Allowed
            } else {
                throw IllegalArgumentException("Period is not ready for completion: ${readiness.reasons.joinToString("; ")}")
            }
        }

        val now = Instant.now()
        val assessments = assessmentRepository.findAllByChildAndLifePeriod(child, period)
        assessments.forEach {
            it.score = periodScoreCalculator.calculate(it)
            it.assessmentDate = now
        }
        val updatedAssessments = assessmentRepository.saveAll(assessments).map { it.toAssessmentResponse() }

        val summary = periodSummaryRepository.findByChildAndLifePeriod(child, period)
            .orElseGet {
                PeriodSummary(
                    child = child,
                    lifePeriod = period,
                    periodStart = LocalDate.now(),
                    periodEnd = LocalDate.now(),
                    isClosed = false
                )
            }

        summary.isClosed = true
        summary.periodEnd = LocalDate.now()
        periodSummaryRepository.save(summary)
        periodSummaryRepository.flush()

        return CompletePeriodAssessmentResult(
            completionPercent = readiness.completionPercent,
            completedAt = now,
            updatedAssessments = updatedAssessments,
            summary = summary
        )
    }

    private data class CompletePeriodAssessmentResult(
        val completionPercent: Int,
        val completedAt: Instant,
        val updatedAssessments: List<AssessmentResponse>,
        val summary: PeriodSummary
    )
}
