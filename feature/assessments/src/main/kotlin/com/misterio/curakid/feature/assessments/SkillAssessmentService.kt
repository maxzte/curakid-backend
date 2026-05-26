package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.AssessmentAnswer
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.reports.PeriodSummary
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class SkillAssessmentService(
    private val assessmentRepository: SkillAssessmentRepository,
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository,
    private val answerOptionRepository: AnswerOptionRepository,
    private val measurementRepository: MeasurementRepository,
    private val periodSummaryRepository: PeriodSummaryRepository,
    private val reportProvider: ReportProvider,
    private val transactionTemplate: TransactionTemplate
) {

    @Transactional
    fun saveAssessmentAnswers(child: Child, periodId: UUID, request: SaveAnswersRequest): List<AssessmentResponse> {
        val period = lifePeriodRepository.findById(periodId)
            .orElseThrow { IllegalStateException("No life period found for id $periodId") }

        // Fetch all questions to determine their domains
        val questions = questionRepository.findAllWithOptionsByIdIn(request.answers.map { it.questionId })
            .associateBy { it.id }

        // Group answers by domain
        val answersByDomain = request.answers.groupBy {
            questions[it.questionId]?.domain ?: throw IllegalArgumentException("Question ${it.questionId} not found")
        }

        val results = mutableListOf<AssessmentResponse>()

        answersByDomain.forEach { (domain, answers) ->
            val assessment = assessmentRepository.findByChildAndDomainAndLifePeriod(child, domain, period)
                .orElseGet {
                    SkillAssessment(
                        child = child,
                        domain = domain,
                        lifePeriod = period,
                        assessmentDate = Instant.now()
                    )
                }

            // Map and add answers to this specific assessment
            val mapped = answers.map { dto ->
                val q = questions[dto.questionId]!!
                val opt = q.options.firstOrNull { it.id == dto.selectedOptionId }
                    ?: throw IllegalArgumentException("Option not found")
                AssessmentAnswer(assessment, q, opt, request.assessmentDate ?: Instant.now())
            }

            mapped.forEach { newAnswer ->
                val existing = assessment.answers.find { it.question?.id == newAnswer.question?.id }
                if (existing != null) {
                    existing.selectedOption = newAnswer.selectedOption
                    existing.answeredAt = newAnswer.answeredAt
                } else {
                    assessment.answers.add(newAnswer)
                }
            }
            assessment.score = null // Reset score since new data arrived

            results.add(assessmentRepository.save(assessment).toAssessmentResponse())
        }

        return results
    }

    @Transactional
    fun updateAssessmentAnswers(assessmentId: UUID, child: Child, request: AnswerRequest): AssessmentResponse {
        val assessment = assessmentRepository.findByIdAndChild(assessmentId, child)
            .orElseThrow { NoSuchElementException("Assessment $assessmentId not found") }

        val existingByQuestionId = assessment.answers.associateBy { it.question?.id }

        val existing = existingByQuestionId[request.questionId]
        if (existing != null) {
            if (existing.selectedOption?.id != request.selectedOptionId) {
                existing.selectedOption = answerOptionRepository.getReferenceById(request.selectedOptionId)
                existing.answeredAt = request.answeredAt ?: Instant.now()
            }
        } else {
            assessment.answers.add(
                AssessmentAnswer(
                    assessment = assessment,
                    question = questionRepository.getReferenceById(request.questionId),
                    selectedOption = answerOptionRepository.getReferenceById(request.selectedOptionId),
                )
            )
        }

        return assessmentRepository.save(assessment).toAssessmentResponse()
    }

    @Transactional(readOnly = true)
    fun getAssessmentsByChild(child: Child): List<AssessmentResponse> {
        return assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child).map { it.toAssessmentResponse() }
    }

    fun completePeriodAssessment(
        child: Child,
        request: CompletePeriodAssessmentRequest
    ): CompletePeriodAssessmentResponse {
        val result = transactionTemplate.execute {
            completePeriodAssessmentTransaction(child, request)
        }

        val reportId = reportProvider.generatePeriodReport(child.id!!, result.summary.id!!)

        return CompletePeriodAssessmentResponse(
            periodCode = request.periodCode,
            completionPercent = result.completionPercent,
            requiredPercent = REQUIRED_COMPLETION_PERCENT,
            completedAt = result.completedAt,
            assessments = result.updatedAssessments,
            reportId = reportId
        )
    }

    private fun completePeriodAssessmentTransaction(
        child: Child,
        request: CompletePeriodAssessmentRequest
    ): CompletePeriodAssessmentResult {
        val period = lifePeriodRepository.findByCodeWithQuestions(request.periodCode)
            .orElseThrow { IllegalArgumentException("Period not found") }

        validatePeriodAccess(child, period.minMonths, period.maxMonths, request.allowEarlyTransition)

        val periodQuestionIds = period.questions.mapNotNull { it.id }.toSet()
        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)

        val answeredQuestionIds = assessments.flatMap { it.answers }
            .mapNotNull { it.question?.id }
            .filter { it in periodQuestionIds }
            .toSet()

        val completionPercent = (answeredQuestionIds.size * 100) / period.questions.size
        if (completionPercent < REQUIRED_COMPLETION_PERCENT) {
            throw IllegalArgumentException("Completion: $completionPercent% < $REQUIRED_COMPLETION_PERCENT%")
        }

        val now = Instant.now()
        val toUpdate = assessments.filter { it.lifePeriod?.id == period.id }
        toUpdate.forEach {
            it.score = calculateScore(it)
            it.assessmentDate = now
        }
        val updatedAssessments = assessmentRepository.saveAll(toUpdate).map { it.toAssessmentResponse() }

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
            completionPercent = completionPercent,
            completedAt = now,
            updatedAssessments = updatedAssessments,
            summary = summary
        )
    }

    private fun upsertAnswer(
        assessment: SkillAssessment,
        questionId: UUID,
        optionId: UUID,
        answeredAt: Instant
    ) {
        val existing = assessment.answers.find { it.question?.id == questionId }
        if (existing != null) {
            if (existing.selectedOption?.id != optionId) {
                existing.selectedOption = answerOptionRepository.getReferenceById(optionId)
                existing.answeredAt = answeredAt
            }
        } else {
            assessment.answers.add(
                AssessmentAnswer(
                    assessment = assessment,
                    question = questionRepository.getReferenceById(questionId),
                    selectedOption = answerOptionRepository.getReferenceById(optionId),
                    answeredAt = answeredAt
                )
            )
        }
    }

    private fun calculateScore(assessment: SkillAssessment): BigDecimal? {
        // Pick only the LATEST answer for each question
        val latestAnswers = assessment.answers
            .filter { it.question?.id != null }
            .groupBy { it.question!!.id }
            .mapValues { entry -> entry.value.maxBy { it.answeredAt } }
            .values
            .filter { it.selectedOption?.value != 0.0 && it.question?.weight != null }

        if (latestAnswers.isEmpty()) return null

        val totalWeight = latestAnswers.sumOf { it.question!!.weight }
        if (totalWeight <= BigDecimal.ZERO) return null

        val weightedSum = latestAnswers.sumOf {
            it.selectedOption!!.value.toBigDecimal().multiply(it.question!!.weight)
        }
        return weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP)
    }

    private data class CompletePeriodAssessmentResult(
        val completionPercent: Int,
        val completedAt: Instant,
        val updatedAssessments: List<AssessmentResponse>,
        val summary: PeriodSummary
    )

    private fun validatePeriodAccess(child: Child, min: Int, max: Int, early: Boolean) {
        val age = ChronoUnit.MONTHS.between(child.birthDate, LocalDate.now()).toInt()
        if (age in min..max || age > max) return

        if (!early || (min - age) > MAX_EARLY_MONTH_GAP) {
            throw IllegalArgumentException("Early transition denied for age $age")
        }

        val measurement = measurementRepository
            .findFirstByChildOrderByMeasurementDateDesc(child)
            .orElseThrow { IllegalArgumentException("No measurements for early transition") }

        val hasSignal = listOfNotNull(measurement.wfaZscore, measurement.hfaZscore, measurement.bmiZscore)
            .any { it >= EARLY_TRANSITION_Z_THRESHOLD }

        if (!hasSignal) throw IllegalArgumentException("Low Z-scores for early transition")
    }

    private companion object {
        private const val REQUIRED_COMPLETION_PERCENT = 80
        private const val MAX_EARLY_MONTH_GAP = 3
        private val EARLY_TRANSITION_Z_THRESHOLD = BigDecimal("1.50")
    }
}
