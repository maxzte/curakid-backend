package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.essential.common.exceptions.OnboardingRequiredException
import com.misterio.curakid.feature.assessments.service.AnswerPersistenceService
import com.misterio.curakid.feature.assessments.service.PeriodCompletionService
import com.misterio.curakid.feature.assessments.service.PeriodReadinessEvaluator
import com.misterio.curakid.core.database.readTx
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import java.util.*

@Service
class SkillAssessmentService(
    private val assessmentRepository: SkillAssessmentRepository,
    private val periodReadinessEvaluator: PeriodReadinessEvaluator,
    private val answerPersistenceService: AnswerPersistenceService,
    private val periodCompletionService: PeriodCompletionService,
    private val childRepository: ChildRepository? = null,
    private val transactionManager: PlatformTransactionManager
) {

    fun findChildOrThrow(childId: UUID, user: User): Child {
        val repo = childRepository ?: throw IllegalStateException("ChildRepository not configured")
        return transactionManager.readTx {
            repo.findByIdAndUser(childId, user).orElseGet {
                if (repo.findAllByUser(user).isEmpty()) {
                    throw OnboardingRequiredException()
                }
                throw NoSuchElementException("Child not found")
            }
        }!!
    }

    fun getAssessmentsByChild(childId: UUID, user: User): List<AssessmentResponse> {
        val child = findChildOrThrow(childId, user)
        return getAssessmentsByChild(child)
    }

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
        return periodReadinessEvaluator.getReadiness(child, periodCode)
    }

    fun saveAssessmentAnswers(child: Child, periodId: UUID, request: SaveAnswersRequest): SaveAnswersResponse {
        return answerPersistenceService.saveAnswers(child, periodId, request)
    }

    fun updateAssessmentAnswers(assessmentId: UUID, child: Child, request: AnswerRequest): AssessmentResponse {
        return answerPersistenceService.updateAnswer(assessmentId, child, request)
    }

    fun getAssessmentsByChild(child: Child): List<AssessmentResponse> {
        return requireNotNull(transactionManager.readTx {
            assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child).map { it.toAssessmentResponse() }
        })
    }

    @Cacheable(value = ["assessmentReadiness"], key = "#child.id.toString() + '_' + #periodCode")
    fun getPeriodReadiness(child: Child, periodCode: String): PeriodReadinessResponse {
        return periodReadinessEvaluator.getReadiness(child, periodCode)
    }

    fun completePeriodAssessment(
        child: Child,
        request: CompletePeriodAssessmentRequest
    ): CompletePeriodAssessmentResponse {
        return periodCompletionService.complete(child, request)
    }
}
