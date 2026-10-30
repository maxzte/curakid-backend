package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.AssessmentAnswer
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import com.misterio.curakid.feature.assessments.AnswerRequest
import com.misterio.curakid.feature.assessments.AssessmentResponse
import com.misterio.curakid.feature.assessments.SaveAnswersRequest
import com.misterio.curakid.feature.assessments.SaveAnswersResponse
import com.misterio.curakid.feature.assessments.toAssessmentResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.UUID

/**
 * Persists assessment answers, applies dynamic skipping and validates
 * logical consistency. Knows nothing about readiness or period completion.
 */
@Component
class AnswerPersistenceService(
    private val assessmentRepository: SkillAssessmentRepository,
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository,
    private val answerOptionRepository: AnswerOptionRepository,
    private val prerequisiteAutoFiller: PrerequisiteAutoFiller,
    private val logicalConsistencyValidator: LogicalConsistencyValidator,
    private val readinessCacheEvictor: ReadinessCacheEvictor,
    private val transactionManager: PlatformTransactionManager
) {

    fun saveAnswers(
        child: Child,
        periodId: UUID,
        request: SaveAnswersRequest
    ): SaveAnswersResponse {
        // Phase 1: Read Context
        val (period, questions, globalHistoryIds) = requireNotNull(transactionManager.readTx {
            val p = lifePeriodRepository.findById(periodId)
                .orElseThrow { IllegalStateException("No life period found for id $periodId") }
            val questionIds = request.answers.map { it.questionId }
            val qMap = questionRepository.findAllWithOptionsByIdIn(questionIds)
                .associateBy { it.id }
            val ghIds = assessmentRepository
                .findAllByChildOrderByAssessmentDateDesc(child)
                .flatMap { it.answers }
                .mapNotNull { it.question?.id }
                .toSet()
            Triple(p, qMap, ghIds)
        })

        readinessCacheEvictor.evict(child.id, period.code)

        // Phase 2: Domain Processing
        val answersByDomain = request.answers.groupBy {
            questions[it.questionId]?.domain ?: throw IllegalArgumentException("Question ${it.questionId} not found")
        }

        val savedAnswers = mutableListOf<AssessmentAnswer>()
        val assessmentsToSave = mutableListOf<SkillAssessment>()

        answersByDomain.forEach { (domain, answers) ->
            val assessment = transactionManager.readTx {
                assessmentRepository.findByChildAndDomainAndLifePeriod(child, domain, period).orElse(null)
            } ?: SkillAssessment(
                child = child,
                domain = domain,
                lifePeriod = period,
                assessmentDate = Instant.now()
            )

            val latestInRequest = answers
                .groupBy { it.questionId }
                .mapValues { entry ->
                    entry.value.maxBy { it.answeredAt ?: Instant.now() }
                }

            val mapped = latestInRequest.values.map { dto ->
                val q = questions[dto.questionId]
                    ?: throw IllegalArgumentException("Question ${dto.questionId} not found")
                val opt = q.options.firstOrNull { it.id == dto.selectedOptionId }
                    ?: throw IllegalArgumentException("Option not found")
                AssessmentAnswer(assessment, q, opt, dto.answeredAt ?: request.assessmentDate ?: Instant.now())
            }

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

                if ((newAnswer.selectedOption?.value ?: 0.0) >= 100.0) {
                    prerequisiteAutoFiller.autoFill(
                        child,
                        period,
                        assessment,
                        newAnswer.question,
                        newAnswer.answeredAt,
                        savedAnswers,
                        globalHistoryIds
                    )
                }
            }
            assessment.score = null
            assessmentsToSave.add(assessment)
        }

        // Phase 3: Write Transaction
        val savedResponses = requireNotNull(transactionManager.writeTx {
            assessmentsToSave.map { assessmentRepository.save(it).toAssessmentResponse() }
        })

        val allChildAssessments = requireNotNull(transactionManager.readTx {
            assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)
        })
        val warnings = logicalConsistencyValidator.validate(allChildAssessments, savedAnswers)

        return SaveAnswersResponse(savedResponses, warnings)
    }

    fun updateAnswer(
        assessmentId: UUID,
        child: Child,
        request: AnswerRequest
    ): AssessmentResponse {
        val (assessment, option, question) = requireNotNull(transactionManager.readTx {
            val a = assessmentRepository.findByIdAndChild(assessmentId, child)
                .orElseThrow { NoSuchElementException("Assessment $assessmentId not found") }
            val opt = answerOptionRepository.getReferenceById(request.selectedOptionId)
            val q = questionRepository.getReferenceById(request.questionId)
            Triple(a, opt, q)
        })

        readinessCacheEvictor.evict(child.id, assessment.lifePeriod?.code)

        val existingByQuestionId = assessment.answers.associateBy { it.question?.id }
        val existing = existingByQuestionId[request.questionId]
        val answeredAt = request.answeredAt ?: Instant.now()

        if (existing != null) {
            if (answeredAt.isAfter(existing.answeredAt)) {
                existing.selectedOption = option
                existing.answeredAt = answeredAt
            }
        } else {
            assessment.answers.add(
                AssessmentAnswer(
                    assessment = assessment,
                    question = question,
                    selectedOption = option,
                    answeredAt = answeredAt
                )
            )
        }

        val saved = requireNotNull(transactionManager.writeTx {
            assessmentRepository.save(assessment)
        })

        return saved.toAssessmentResponse()
    }
}
