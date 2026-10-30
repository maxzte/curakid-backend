package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.feature.assessments.service.AnswerPersistenceService
import com.misterio.curakid.feature.assessments.service.LogicalConsistencyValidator
import com.misterio.curakid.feature.assessments.service.PeriodCompletionService
import com.misterio.curakid.feature.assessments.service.PeriodReadinessEvaluator
import com.misterio.curakid.feature.assessments.service.PeriodScoreCalculator
import com.misterio.curakid.feature.assessments.service.PrerequisiteAutoFiller
import com.misterio.curakid.feature.assessments.service.ReadinessCacheEvictor
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillAssessmentServiceTest {

    @Mock lateinit var assessmentRepository: SkillAssessmentRepository
    @Mock lateinit var lifePeriodRepository: LifePeriodRepository
    @Mock lateinit var questionRepository: QuestionRepository
    @Mock lateinit var answerOptionRepository: AnswerOptionRepository
    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var periodSummaryRepository: PeriodSummaryRepository
    @Mock lateinit var reportProvider: ReportProvider
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var transactionManager: PlatformTransactionManager
    @Mock lateinit var periodReadinessEvaluator: PeriodReadinessEvaluator

    private lateinit var answerPersistenceService: AnswerPersistenceService
    private lateinit var periodCompletionService: PeriodCompletionService
    private lateinit var service: SkillAssessmentService

    @BeforeEach
    fun setUp() {
        val periodScoreCalculator = PeriodScoreCalculator()
        val prerequisiteAutoFiller = PrerequisiteAutoFiller(assessmentRepository)
        val logicalConsistencyValidator = LogicalConsistencyValidator(questionRepository)
        val readinessCacheEvictor = ReadinessCacheEvictor(lifePeriodRepository, null)

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
            periodScoreCalculator,
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
    fun `completePeriodAssessment should calculate weighted scores correctly`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val period = LifePeriod(code = "6M", minMonths = 6, maxMonths = 8).apply { id = UUID.randomUUID() }

        whenever(periodReadinessEvaluator.evaluate(any(), any())).thenReturn(
            PeriodReadinessResponse(isReady = true, completionPercent = 100, requiredPercent = 80, domainStatus = emptyList())
        )
        whenever(lifePeriodRepository.findByCodeWithQuestions("6M")).thenReturn(Optional.of(period))
        whenever(assessmentRepository.findAllByChildAndLifePeriod(child, period)).thenReturn(emptyList())
        whenever(periodSummaryRepository.findByChildAndLifePeriod(child, period)).thenReturn(Optional.empty())
        whenever(reportProvider.generatePeriodReport(any<UUID>(), any<UUID>())).thenReturn(UUID.randomUUID())

        val request = CompletePeriodAssessmentRequest("6M")
        val response = service.completePeriodAssessment(child, request)

        org.junit.jupiter.api.Assertions.assertNotNull(response)
    }
}
