package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.AssessmentAnswer
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.reports.PeriodSummary
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.essential.common.exceptions.OnboardingRequiredException
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
    private val transactionTemplate: TransactionTemplate,
    private val childRepository: ChildRepository? = null,
    private val cacheManager: org.springframework.cache.CacheManager? = null
) {

    fun findChildOrThrow(childId: UUID, user: User): Child {
        val repo = childRepository ?: throw IllegalStateException("ChildRepository not configured")
        return repo.findByIdAndUser(childId, user).orElseGet {
            if (repo.findAllByUser(user).isEmpty()) {
                throw OnboardingRequiredException()
            }
            throw NoSuchElementException("Child not found")
        }
    }

    fun getAssessmentsByChild(childId: UUID, user: User): List<AssessmentResponse> {
        val child = findChildOrThrow(childId, user)
        return getAssessmentsByChild(child)
    }

    @Transactional
    fun saveAssessmentAnswers(childId: UUID, periodId: UUID, user: User, request: SaveAnswersRequest): SaveAnswersResponse {
        val child = findChildOrThrow(childId, user)
        return saveAssessmentAnswers(child, periodId, request)
    }

    fun updateAssessmentAnswers(assessmentId: UUID, childId: UUID, user: User, request: AnswerRequest): AssessmentResponse {
        val child = findChildOrThrow(childId, user)
        return updateAssessmentAnswers(assessmentId, child, request)
    }

    fun completePeriodAssessment(childId: UUID, user: User, request: CompletePeriodAssessmentRequest): CompletePeriodAssessmentResponse {
        val child = findChildOrThrow(childId, user)
        return completePeriodAssessment(child, request)
    }

    fun getPeriodReadiness(childId: UUID, periodCode: String, user: User): PeriodReadinessResponse {
        val child = findChildOrThrow(childId, user)
        return getPeriodReadiness(child, periodCode)
    }

    private fun evictReadinessForChild(childId: UUID?, periodCode: String? = null) {
        if (childId == null) return
        val cache = cacheManager?.getCache("assessmentReadiness") ?: return

        if (periodCode != null) {
            cache.evict("${childId}_$periodCode")
        }

        val nativeMap = cache.nativeCache
        if (nativeMap is Map<*, *>) {
            val prefix = "${childId}_"
            val keysToEvict = nativeMap.keys.filterIsInstance<String>().filter { it.startsWith(prefix) }
            keysToEvict.forEach { cache.evict(it) }
        }
    }

    @Transactional
    fun saveAssessmentAnswers(child: Child, periodId: UUID, request: SaveAnswersRequest): SaveAnswersResponse {
        val period = lifePeriodRepository.findById(periodId)
            .orElseThrow { IllegalStateException("No life period found for id $periodId") }
        evictReadinessForChild(child.id, period.code)

        // Fetch all questions to determine their domains and prerequisites
        val questionIds = request.answers.map { it.questionId }
        val questions = questionRepository.findAllWithOptionsByIdIn(questionIds)
            .associateBy { it.id }

        // Group answers by domain
        val answersByDomain = request.answers.groupBy {
            questions[it.questionId]?.domain ?: throw IllegalArgumentException("Question ${it.questionId} not found")
        }

        val results = mutableListOf<AssessmentResponse>()
        val savedAnswers = mutableListOf<AssessmentAnswer>()

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

            val latestInRequest = answers
                .groupBy { it.questionId }
                .mapValues { entry ->
                    entry.value.maxBy { it.answeredAt ?: Instant.now() }
                }

            val mapped = latestInRequest.values.map { dto ->
                val q = questions[dto.questionId]!!
                val opt = q.options.firstOrNull { it.id == dto.selectedOptionId }
                    ?: throw IllegalArgumentException("Option not found")
                AssessmentAnswer(assessment, q, opt, dto.answeredAt ?: request.assessmentDate ?: Instant.now())
            }

            val allChildAssessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)
            val globalHistoryIds = allChildAssessments.flatMap { it.answers }.mapNotNull { it.question?.id }.toSet()

            mapped.forEach { newAnswer ->
                val existing = assessment.answers.find { it.question?.id == newAnswer.question?.id }
                if (existing != null) {
                    if (newAnswer.answeredAt.isAfter(existing.answeredAt)) {
                        existing.selectedOption = newAnswer.selectedOption
                        existing.answeredAt = newAnswer.answeredAt
                    }
                } else {
                    assessment.answers.add(newAnswer)
                }
                savedAnswers.add(newAnswer)
                
                // Dynamic skipping: if this is a "Yes" (100%), auto-fill prerequisites
                if ((newAnswer.selectedOption?.value ?: 0.0) >= 100.0) {
                    autoFillPrerequisites(assessment, newAnswer.question, newAnswer.answeredAt, savedAnswers, globalHistoryIds)
                }
            }
            assessment.score = null 

            results.add(assessmentRepository.save(assessment).toAssessmentResponse())
        }

        val allChildAssessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)
        val warnings = validateLogicalConsistency(allChildAssessments, savedAnswers)

        return SaveAnswersResponse(results, warnings)
    }

    private fun autoFillPrerequisites(
        assessment: SkillAssessment,
        question: Question?,
        timestamp: Instant,
        savedAnswers: MutableList<AssessmentAnswer>,
        globalHistoryIds: Set<UUID>
    ) {
        var currentPrereq = question?.prerequisite
        var depth = 0
        val MAX_DEPTH = 10 // Safety limit for recursion

        while (currentPrereq != null && depth < MAX_DEPTH) {
            val isAnsweredLocally = assessment.answers.any { it.question?.id == currentPrereq?.id }
            val isAnsweredGlobally = globalHistoryIds.contains(currentPrereq.id)
            
            if (!isAnsweredLocally && !isAnsweredGlobally) {
                // Find "Yes" option for this prerequisite
                val yesOption = currentPrereq.options.find { it.value >= 100.0 }
                if (yesOption != null) {
                    val autoAnswer = AssessmentAnswer(
                        assessment = assessment,
                        question = currentPrereq,
                        selectedOption = yesOption,
                        answeredAt = timestamp
                    )
                    assessment.answers.add(autoAnswer)
                    savedAnswers.add(autoAnswer) // Include in final response and validation
                }
            }
            currentPrereq = currentPrereq.prerequisite
            depth++
        }
    }

    private fun validateLogicalConsistency(
        allAssessments: List<SkillAssessment>,
        newAnswers: List<AssessmentAnswer>
    ): List<String> {
        val warnings = mutableListOf<String>()
        
        // Contextual historical map
        val allAnswers = allAssessments.flatMap { it.answers }
            .filter { it.question?.id != null }
            .associateBy { it.question!!.id!! }

        val noAnswerQuestions = newAnswers
            .filter { (it.selectedOption?.value ?: 0.0) < 100.0 }
            .mapNotNull { it.question }

        val dependentQuestionsMap = if (noAnswerQuestions.isNotEmpty()) {
            questionRepository.findAllByPrerequisiteIn(noAnswerQuestions)
                .groupBy { it.prerequisite?.id }
        } else emptyMap()

        newAnswers.forEach { answer ->
            val question = answer.question ?: return@forEach
            val selectedValue = answer.selectedOption?.value ?: return@forEach
            
            // 1. Check if "Yes" (100) has a "No" (0) prerequisite
            if (selectedValue >= 100.0) {
                val prereq = question.prerequisite
                if (prereq != null) {
                    val prereqAnswer = allAnswers[prereq.id]
                    if (prereqAnswer != null && (prereqAnswer.selectedOption?.value ?: 0.0) < 100.0) {
                        warnings.add("Схоже, сталася помилка. Навичка '${question.text}' зазвичай з'являється після того, як дитина навчиться '${prereq.text}'.")
                    }
                }
            }
            
            // 2. Check if "No" (0) is a prerequisite for some other "Yes" skill already in the system
            if (selectedValue < 100.0) {
                val dependentQuestions = dependentQuestionsMap[question.id] ?: emptyList()
                dependentQuestions.forEach { depQ ->
                    val depAnswer = allAnswers[depQ.id]
                    if (depAnswer != null && (depAnswer.selectedOption?.value ?: 0.0) >= 100.0) {
                        warnings.add("Ви вказали, що дитина ще не опанувала '${question.text}', хоча раніше було відмічено успіх у '${depQ.text}'. Навичка '${depQ.text}' базується на '${question.text}'.")
                    }
                }
            }
        }
        
        return warnings.distinct()
    }

    @Transactional
    fun updateAssessmentAnswers(assessmentId: UUID, child: Child, request: AnswerRequest): AssessmentResponse {
        evictReadinessForChild(child.id)
        val assessment = assessmentRepository.findByIdAndChild(assessmentId, child)
            .orElseThrow { NoSuchElementException("Assessment $assessmentId not found") }

        val existingByQuestionId = assessment.answers.associateBy { it.question?.id }

        val existing = existingByQuestionId[request.questionId]
        val answeredAt = request.answeredAt ?: Instant.now()
        if (existing != null) {
            // LWW logic: only update if incoming timestamp is newer
            if (answeredAt.isAfter(existing.answeredAt)) {
                existing.selectedOption = answerOptionRepository.getReferenceById(request.selectedOptionId)
                existing.answeredAt = answeredAt
            }
        } else {
            assessment.answers.add(
                AssessmentAnswer(
                    assessment = assessment,
                    question = questionRepository.getReferenceById(request.questionId),
                    selectedOption = answerOptionRepository.getReferenceById(request.selectedOptionId),
                    answeredAt = answeredAt
                )
            )
        }

        return assessmentRepository.save(assessment).toAssessmentResponse()
    }

    @Transactional(readOnly = true)
    fun getAssessmentsByChild(child: Child): List<AssessmentResponse> {
        return assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child).map { it.toAssessmentResponse() }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = ["assessmentReadiness"], key = "#child.id.toString() + '_' + #periodCode")
    fun getPeriodReadiness(child: Child, periodCode: String): PeriodReadinessResponse {
        val period = lifePeriodRepository.findByCodeWithQuestions(periodCode)
            .orElseThrow { IllegalArgumentException("Period not found") }

        return evaluateReadiness(child, period)
    }

    private fun evaluateReadiness(child: Child, period: LifePeriod): PeriodReadinessResponse {
        val correctedAge = child.calculateCorrectedAgeMonths()
        val isAgeAppropriate = correctedAge >= period.minMonths

        val periodQuestionIds = period.questions.mapNotNull { it.id }.toSet()
        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)

        val answeredQuestionIds = assessments.flatMap { it.answers }
            .mapNotNull { it.question?.id }
            .filter { it in periodQuestionIds }
            .toSet()

        val totalQuestions = period.questions.size
        if (totalQuestions == 0) return PeriodReadinessResponse(false, 0, REQUIRED_COMPLETION_PERCENT, emptyList())

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
                isReady = domainPercent >= REQUIRED_DOMAIN_COMPLETION_PERCENT,
                requiredPercent = REQUIRED_DOMAIN_COMPLETION_PERCENT
            )
        }

        val reasons = mutableListOf<String>()
        if (totalCompletionPercent < REQUIRED_COMPLETION_PERCENT) {
            reasons.add("Загальний прогрес: $totalCompletionPercent% < $REQUIRED_COMPLETION_PERCENT%")
        }
        domainStatus.filter { !it.isReady }.forEach {
            reasons.add("Недостатньо даних у розділі ${it.domain}: ${it.completionPercent}% < $REQUIRED_DOMAIN_COMPLETION_PERCENT%")
        }
        
        var canTransitionEarly = false
        if (!isAgeAppropriate) {
            try {
                validatePeriodAccess(child, period.minMonths, period.maxMonths, true)
                canTransitionEarly = true
            } catch (e: Exception) {
                reasons.add("Дитина ще занадто мала для цього періоду (потрібно від ${period.minMonths} міс., коригований вік $correctedAge міс.)")
            }
        }

        val isReady = totalCompletionPercent >= REQUIRED_COMPLETION_PERCENT && 
                      domainStatus.all { it.isReady } && 
                      (isAgeAppropriate || canTransitionEarly)

        return PeriodReadinessResponse(
            isReady = isReady,
            completionPercent = totalCompletionPercent,
            requiredPercent = REQUIRED_COMPLETION_PERCENT,
            domainStatus = domainStatus,
            reasons = reasons,
            canTransitionEarly = canTransitionEarly
        )
    }

    fun completePeriodAssessment(
        child: Child,
        request: CompletePeriodAssessmentRequest
    ): CompletePeriodAssessmentResponse {
        evictReadinessForChild(child.id)
        val result = transactionTemplate.execute {
            completePeriodAssessmentTransaction(child, request)
        } ?: throw IllegalStateException("Period assessment transaction failed or was rolled back")

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

        val readiness = evaluateReadiness(child, period)
        
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
            it.score = calculateScore(it)
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

    private fun calculateScore(assessment: SkillAssessment): BigDecimal? {
        val latestAnswers = assessment.answers
            .filter { it.question?.id != null }
            .groupBy { it.question!!.id }
            .mapValues { entry -> entry.value.maxBy { it.answeredAt } }
            .values
            .filter { it.selectedOption != null && it.question?.weight != null }

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
        val correctedAge = child.calculateCorrectedAgeMonths()
        // age in range  → normal access
        // age > max     → child already exceeded this period; completing it is always allowed
        //                 (catch-up scenario), no early-transition checks needed
        if (correctedAge in min..max || correctedAge > max) return

        // age < min — early transition; requires explicit flag and Z-score signal
        if (!early || (min - correctedAge) > MAX_EARLY_MONTH_GAP) {
            throw IllegalArgumentException("Early transition denied for corrected age $correctedAge")
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
        private const val REQUIRED_DOMAIN_COMPLETION_PERCENT = 50
        private const val MAX_EARLY_MONTH_GAP = 3
        private val EARLY_TRANSITION_Z_THRESHOLD = BigDecimal("1.50")
    }
}
