package com.misterio.curakid.assessments

import com.misterio.curakid.children.Child
import com.misterio.curakid.development.LifePeriodRepository
import com.misterio.curakid.development.Question
import com.misterio.curakid.development.QuestionRepository
import com.misterio.curakid.measurements.MeasurementRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class SkillAssessmentService(
    private val assessmentRepository: SkillAssessmentRepository,
    private val questionRepository: QuestionRepository,
    private val lifePeriodRepository: LifePeriodRepository,
    private val measurementRepository: MeasurementRepository
) {
    private val log = LoggerFactory.getLogger(SkillAssessmentService::class.java)

    companion object {
        private const val REQUIRED_COMPLETION_PERCENT = 80
        private const val MAX_EARLY_MONTH_GAP = 3
        private val EARLY_TRANSITION_Z_THRESHOLD = BigDecimal("1.50")
    }

    fun getAssessments(child: Child, domain: AssessmentDomain?): List<AssessmentResponse> {
        val results = if (domain != null) {
            assessmentRepository.findAllByChildAndDomain(child, domain)
        } else {
            assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)
        }
        return results.map { AssessmentResponse.from(it) }
    }

    @Transactional
    fun saveAssessment(child: Child, request: SaveAssessmentRequest): AssessmentResponse {
        val assessment = SkillAssessment(
            child = child,
            domain = request.domain,
            score = null,
            rawData = normalizeRawData(request.rawData),
            assessmentDate = request.assessmentDate ?: LocalDateTime.now()
        )
        return AssessmentResponse.from(assessmentRepository.save(assessment))
    }

    @Transactional
    fun updateAssessment(child: Child, assessmentId: UUID, request: SaveAssessmentRequest): AssessmentResponse {
        val existing = assessmentRepository.findByIdAndChild(assessmentId, child)
            .orElseThrow { NoSuchElementException("Assessment not found") }

        val mergedRawData = mergeRawData(existing.rawData, normalizeRawData(request.rawData))
        existing.domain = request.domain
        existing.rawData = mergedRawData
        existing.assessmentDate = request.assessmentDate ?: existing.assessmentDate
        existing.score = null

        return AssessmentResponse.from(assessmentRepository.save(existing))
    }

    @Transactional
    fun completePeriodAssessment(
        child: Child,
        request: CompletePeriodAssessmentRequest
    ): CompletePeriodAssessmentResponse {
        val period = lifePeriodRepository.findByCodeWithQuestions(request.periodCode)
            .orElseThrow { IllegalArgumentException("Life period '${request.periodCode}' not found") }

        val periodQuestionIds = period.questions.mapNotNull { it.id }
        if (periodQuestionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(periodQuestionIds)
        }

        validatePeriodAccess(child, period.minMonths, period.maxMonths, request.allowEarlyTransition)

        val periodQuestions = period.questions
        if (periodQuestions.isEmpty()) {
            throw IllegalArgumentException("Life period '${request.periodCode}' has no questions")
        }
        val periodQuestionIdsSet = periodQuestionIds.toSet()
        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)
        val now = LocalDateTime.now()

        val answeredQuestionIds = assessments
            .flatMap { assessment ->
                assessment.rawData.orEmpty().keys.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            }
            .filter { it in periodQuestionIdsSet }
            .toSet()

        val completionPercent = (answeredQuestionIds.size * 100) / periodQuestionIds.size
        if (completionPercent < REQUIRED_COMPLETION_PERCENT) {
            throw IllegalArgumentException(
                "Insufficient completion: $completionPercent%, required: $REQUIRED_COMPLETION_PERCENT%"
            )
        }

        val periodQuestionsByDomain = periodQuestions.groupBy { it.domain }
        val updatedAssessments = assessments
            .filter { it.domain in periodQuestionsByDomain.keys }
            .mapNotNull { assessment ->
                val domainQuestionIds = periodQuestionsByDomain[assessment.domain]
                    ?.mapNotNull { it.id }
                    ?.toSet()
                    ?: emptySet()
                val domainRawData = assessment.rawData.orEmpty()
                    .filterKeys { key -> runCatching { UUID.fromString(key) }.getOrNull() in domainQuestionIds }
                if (domainRawData.isEmpty()) {
                    return@mapNotNull null
                }
                assessment.score = calculateScore(assessment.domain, domainRawData)
                assessment.assessmentDate = now
                AssessmentResponse.from(assessmentRepository.save(assessment))
            }

        return CompletePeriodAssessmentResponse(
            periodCode = request.periodCode,
            completionPercent = completionPercent,
            requiredPercent = REQUIRED_COMPLETION_PERCENT,
            completedAt = now,
            assessments = updatedAssessments
        )
    }

    private fun calculateScore(domain: AssessmentDomain, rawData: Map<String, Any>?): BigDecimal? {
        if (rawData.isNullOrEmpty()) return null

        val questionIds = rawData.keys.mapNotNull {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        if (questionIds.isEmpty()) return null

        val questions = questionRepository.findAllWithOptionsByIdInAndDomain(questionIds, domain)
        if (questions.isEmpty()) return null

        var weightedSum = BigDecimal.ZERO
        var totalWeight = BigDecimal.ZERO

        for (question in questions) {
            val questionId = question.id ?: continue
            val rawAnswer = rawData[questionId.toString()] ?: continue
            val optionScore = resolveOptionScore(question, rawAnswer) ?: continue
            weightedSum = weightedSum.add(optionScore.multiply(question.weight))
            totalWeight = totalWeight.add(question.weight)
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) return null
        return weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP)
    }

    private fun resolveOptionScore(question: Question, rawAnswer: Any): BigDecimal? {
        val answer = extractRawAnswerValue(rawAnswer)

        question.options.firstOrNull { it.id?.toString() == answer }?.let { option ->
            return option.value.toBigDecimalOrNull()
        }
        question.options.firstOrNull { it.value == answer }?.let { option ->
            return option.value.toBigDecimalOrNull()
        }

        val numeric = answer.toBigDecimalOrNull()
        if (numeric == null) {
            log.debug("Cannot parse score from answer '{}' for question {}", answer, question.id)
        }
        return numeric
    }

    private fun extractRawAnswerValue(rawAnswer: Any): String {
        if (rawAnswer is Map<*, *>) {
            return rawAnswer["value"]?.toString() ?: ""
        }
        return rawAnswer.toString()
    }

    private fun normalizeRawData(rawData: Map<String, Any>?): Map<String, Any>? {
        if (rawData.isNullOrEmpty()) return rawData
        val now = LocalDateTime.now().toString()
        return rawData.mapValues { (_, value) ->
            if (value is Map<*, *> && value.containsKey("value")) {
                mapOf(
                    "value" to value["value"].toString(),
                    "answeredAt" to (value["answeredAt"]?.toString() ?: now)
                )
            } else {
                mapOf(
                    "value" to value.toString(),
                    "answeredAt" to now
                )
            }
        }
    }

    private fun mergeRawData(
        existingRawData: Map<String, Any>?,
        incomingRawData: Map<String, Any>?
    ): Map<String, Any>? {
        if (existingRawData.isNullOrEmpty()) return incomingRawData
        if (incomingRawData.isNullOrEmpty()) return existingRawData
        return existingRawData + incomingRawData
    }

    private fun validatePeriodAccess(child: Child, minMonths: Int, maxMonths: Int, allowEarlyTransition: Boolean) {
        val ageMonths = ChronoUnit.MONTHS.between(child.birthDate, LocalDate.now()).toInt()

        if (ageMonths in minMonths..maxMonths) return
        if (ageMonths > maxMonths) return

        val monthGap = minMonths - ageMonths
        if (!allowEarlyTransition || monthGap > MAX_EARLY_MONTH_GAP) {
            throw IllegalArgumentException(
                "Early transition is not allowed for current age ($ageMonths months), required min: $minMonths"
            )
        }

        val latestMeasurement = measurementRepository.findAllByChildOrderByMeasurementDateAsc(child).lastOrNull()
            ?: throw IllegalArgumentException("Early transition requires at least one measurement")

        val hasAdvancedSignal = listOfNotNull(
            latestMeasurement.wfaZscore,
            latestMeasurement.hfaZscore,
            latestMeasurement.bmiZscore
        ).any { it >= EARLY_TRANSITION_Z_THRESHOLD }

        if (!hasAdvancedSignal) {
            throw IllegalArgumentException(
                "Early transition requires at least one Z-score >= $EARLY_TRANSITION_Z_THRESHOLD"
            )
        }
    }
}
