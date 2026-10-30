package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.feature.assessments.service.AnswerPersistenceService
import com.misterio.curakid.feature.assessments.service.LogicalConsistencyValidator
import com.misterio.curakid.feature.assessments.service.PeriodCompletionService
import com.misterio.curakid.feature.assessments.service.PeriodReadinessEvaluator
import com.misterio.curakid.feature.assessments.service.PrerequisiteAutoFiller
import com.misterio.curakid.feature.assessments.service.ReadinessCacheEvictor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillAssessmentDynamicSkippingTest {

    @Mock lateinit var assessmentRepository: SkillAssessmentRepository
    @Mock lateinit var lifePeriodRepository: LifePeriodRepository
    @Mock lateinit var questionRepository: QuestionRepository
    @Mock lateinit var answerOptionRepository: AnswerOptionRepository
    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var periodSummaryRepository: PeriodSummaryRepository
    @Mock lateinit var reportProvider: ReportProvider
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var transactionManager: PlatformTransactionManager
    @Mock lateinit var logicalConsistencyValidator: LogicalConsistencyValidator
    @Mock lateinit var periodReadinessEvaluator: PeriodReadinessEvaluator
    @Mock lateinit var readinessCacheEvictor: ReadinessCacheEvictor

    private lateinit var answerPersistenceService: AnswerPersistenceService
    private lateinit var periodCompletionService: PeriodCompletionService
    private lateinit var service: SkillAssessmentService

    @BeforeEach
    fun setUp() {
        val prerequisiteAutoFiller = PrerequisiteAutoFiller(assessmentRepository)

        answerPersistenceService = AnswerPersistenceService(
            assessmentRepository,
            lifePeriodRepository,
            questionRepository,
            answerOptionRepository,
            prerequisiteAutoFiller,
            logicalConsistencyValidator,
            readinessCacheEvictor,
            transactionManager
        )

        periodCompletionService = PeriodCompletionService(
            assessmentRepository,
            lifePeriodRepository,
            periodSummaryRepository,
            reportProvider,
            transactionManager,
            periodReadinessEvaluator,
            mock(),
            readinessCacheEvictor
        )

        service = SkillAssessmentService(
            assessmentRepository = assessmentRepository,
            periodReadinessEvaluator = periodReadinessEvaluator,
            answerPersistenceService = answerPersistenceService,
            periodCompletionService = periodCompletionService,
            childRepository = childRepository,
            transactionManager = transactionManager
        )
    }

    @Test
    fun `saveAssessmentAnswers should auto-fill prerequisites when answering Yes`() {
        val child = Child().apply { id = UUID.randomUUID() }
        val periodId = UUID.randomUUID()
        val period = LifePeriod().apply { id = periodId }

        val mainQId = UUID.randomUUID()
        val prereqQId = UUID.randomUUID()

        val prereqQ = Question(text = "Prereq Q", domain = AssessmentDomain.MOTOR_GROSS).apply { id = prereqQId }
        val yesOpt = AnswerOption(text = "Yes", value = 100.0)
        prereqQ.options.add(yesOpt)

        val mainQ = Question(text = "Main Q", domain = AssessmentDomain.MOTOR_GROSS, prerequisite = prereqQ).apply { id = mainQId }
        val mainYesOpt = AnswerOption(text = "Yes", value = 100.0).apply { id = UUID.randomUUID() }
        mainQ.options.add(mainYesOpt)

        whenever(lifePeriodRepository.findById(periodId)).thenReturn(Optional.of(period))
        whenever(questionRepository.findAllWithOptionsByIdIn(listOf(mainQId))).thenReturn(listOf(mainQ))
        whenever(assessmentRepository.findByChildAndDomainAndLifePeriod(child, AssessmentDomain.MOTOR_GROSS, period))
            .thenReturn(Optional.empty())

        whenever(assessmentRepository.save(any<SkillAssessment>())).thenAnswer { it.arguments[0] }
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())

        val request = SaveAnswersRequest(
            answers = listOf(
                AnswerRequest(questionId = mainQId, selectedOptionId = mainYesOpt.id!!)
            )
        )

        val response = service.saveAssessmentAnswers(child, periodId, request)

        assertEquals(1, response.assessments.size)
        verify(assessmentRepository).save(any())
    }
}
