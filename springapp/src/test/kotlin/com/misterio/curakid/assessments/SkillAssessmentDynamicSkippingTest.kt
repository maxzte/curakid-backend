package com.misterio.curakid.assessments

import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.feature.assessments.AnswerRequest
import com.misterio.curakid.feature.assessments.SaveAnswersRequest
import com.misterio.curakid.feature.assessments.SkillAssessmentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class SkillAssessmentDynamicSkippingTest {

    private lateinit var assessmentRepository: SkillAssessmentRepository
    private lateinit var lifePeriodRepository: LifePeriodRepository
    private lateinit var questionRepository: QuestionRepository
    private lateinit var answerOptionRepository: AnswerOptionRepository
    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var periodSummaryRepository: PeriodSummaryRepository
    private lateinit var reportProvider: ReportProvider
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var service: SkillAssessmentService

    @BeforeEach
    fun setUp() {
        assessmentRepository = mock()
        lifePeriodRepository = mock()
        questionRepository = mock()
        answerOptionRepository = mock()
        measurementRepository = mock()
        periodSummaryRepository = mock()
        reportProvider = mock()
        transactionTemplate = mock()
        
        service = SkillAssessmentService(
            assessmentRepository,
            lifePeriodRepository,
            questionRepository,
            answerOptionRepository,
            measurementRepository,
            periodSummaryRepository,
            reportProvider,
            transactionTemplate
        )
    }

    @Test
    fun `saveAssessmentAnswers should auto-fill prerequisites when answering Yes`() {
        val child = Child().apply { id = UUID.randomUUID() }
        val periodId = UUID.randomUUID()
        val period = LifePeriod().apply { id = periodId }
        
        // Setup questions: Q1 (Prereq) <- Q2 (Target)
        val q1Id = UUID.randomUUID()
        val q2Id = UUID.randomUUID()
        
        val yesOpt1 = AnswerOption(text = "Yes", value = 100.0).apply { id = UUID.randomUUID() }
        val q1 = Question(text = "Base Skill", domain = AssessmentDomain.MOTOR_GROSS, weight = BigDecimal.ONE).apply {
            id = q1Id
            options.add(yesOpt1)
        }
        
        val yesOpt2 = AnswerOption(text = "Yes", value = 100.0).apply { id = UUID.randomUUID() }
        val q2 = Question(text = "Advanced Skill", domain = AssessmentDomain.MOTOR_GROSS, weight = BigDecimal.ONE).apply {
            id = q2Id
            prerequisite = q1
            options.add(yesOpt2)
        }
        
        whenever(lifePeriodRepository.findById(periodId)).thenReturn(Optional.of(period))
        whenever(questionRepository.findAllWithOptionsByIdIn(any())).thenReturn(listOf(q2))
        whenever(assessmentRepository.findByChildAndDomainAndLifePeriod(eq(child), eq(AssessmentDomain.MOTOR_GROSS), eq(period)))
            .thenReturn(Optional.empty())
        whenever(assessmentRepository.save(any<SkillAssessment>())).thenAnswer { it.arguments[0] }
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(any())).thenReturn(emptyList())
        whenever(questionRepository.findAllByPrerequisite(any())).thenReturn(emptyList())

        val request = SaveAnswersRequest(
            answers = listOf(AnswerRequest(q2Id, yesOpt2.id!!, Instant.now()))
        )

        val response = service.saveAssessmentAnswers(child, periodId, request)
        
        // Verification
        val assessment = argumentCaptor<SkillAssessment>()
        verify(assessmentRepository).save(assessment.capture())
        
        val savedAnswers = assessment.firstValue.answers
        assertEquals(2, savedAnswers.size, "Should have 2 answers: one for Q2 and one auto-filled for Q1")
        
        val q1Answer = savedAnswers.find { it.question?.id == q1Id }
        val q2Answer = savedAnswers.find { it.question?.id == q2Id }
        
        assertTrue(q1Answer != null, "Q1 should be auto-filled")
        assertEquals(100.0, q1Answer?.selectedOption?.value)
        assertEquals(100.0, q2Answer?.selectedOption?.value)
    }
}
