package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReport
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.database.reports.PeriodSummary
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeProvider
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock lateinit var aiReportRepository: AiReportRepository
    @Mock lateinit var periodSummaryRepository: PeriodSummaryRepository
    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var assessmentRepository: SkillAssessmentRepository
    @Mock lateinit var lifePeriodRepository: LifePeriodRepository
    @Mock lateinit var fuzzyRuleRepository: FuzzyRuleRepository
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var intelligenceProvider: IntelligenceProvider
    @Mock lateinit var knowledgeProvider: KnowledgeProvider
    @Mock lateinit var chatClient: ChatClient
    @Mock lateinit var percentileCalculator: WhoPercentileCalculator
    @Mock lateinit var transactionManager: PlatformTransactionManager
    @Mock lateinit var eventPublisher: ApplicationEventPublisher

    private val measurementCalculator = MeasurementCalculator()
    private lateinit var reportContextBuilder: ReportContextBuilder
    private lateinit var reportGenerationEngine: ReportGenerationEngine
    private lateinit var reportService: ReportService

    @BeforeEach
    fun setUp() {
        reportContextBuilder = ReportContextBuilder(
            childRepository = childRepository,
            measurementRepository = measurementRepository,
            assessmentRepository = assessmentRepository,
            lifePeriodRepository = lifePeriodRepository,
            periodSummaryRepository = periodSummaryRepository,
            fuzzyRuleRepository = fuzzyRuleRepository,
            percentileCalculator = percentileCalculator,
            measurementCalculator = measurementCalculator,
            cacheManager = null
        )

        reportGenerationEngine = ReportGenerationEngine(
            intelligenceProvider = intelligenceProvider,
            knowledgeProvider = knowledgeProvider,
            chatClient = chatClient,
            promptBuilder = ReportPromptBuilder(),
            reportEvaluator = ReportEvaluator(
                intelligenceProvider = intelligenceProvider,
                aiReportRepository = aiReportRepository,
                transactionManager = transactionManager
            ),
            reportContextBuilder = reportContextBuilder
        )

        reportService = ReportService(
            aiReportRepository = aiReportRepository,
            periodSummaryRepository = periodSummaryRepository,
            childRepository = childRepository,
            measurementRepository = measurementRepository,
            transactionManager = transactionManager,
            taskExecutor = org.springframework.core.task.SyncTaskExecutor(),
            eventPublisher = eventPublisher,
            reportContextBuilder = reportContextBuilder,
            reportGenerationEngine = reportGenerationEngine
        )
    }

    @Test
    fun `generateEmergencyReport should create placeholder and return its ID`() {
        val childId = UUID.randomUUID()
        val measurementId = UUID.randomUUID()
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = childId }
        val measurement = com.misterio.curakid.core.database.measurements.Measurement(
            child = child, measurementDate = Instant.now()
        ).apply { id = measurementId }

        whenever(childRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(measurementRepository.findById(measurementId)).thenReturn(Optional.of(measurement))
        whenever(measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(any(), any())).thenReturn(emptyList())
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        val reportId = reportService.generateEmergencyReport(childId, measurementId)

        assertNotNull(reportId)
        verify(aiReportRepository, atLeastOnce()).save(any())
    }

    @Test
    fun `generatePeriodReport should create placeholder and return its ID`() {
        val childId = UUID.randomUUID()
        val periodId = UUID.randomUUID()
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = childId }
        val summary = PeriodSummary(child = child).apply { id = periodId }

        whenever(childRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(periodSummaryRepository.findById(periodId)).thenReturn(Optional.of(summary))
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        val reportId = reportService.generatePeriodReport(childId, periodId)

        assertNotNull(reportId)
        verify(aiReportRepository, atLeastOnce()).save(any())
    }

    @Test
    fun `getReportStatus should return true if report text is present`() {
        val reportId = UUID.randomUUID()
        val user = com.misterio.curakid.core.database.auth.User().apply { id = UUID.randomUUID() }
        val report = AiReport(reportText = "Done").apply { id = reportId }
        whenever(aiReportRepository.findByIdAndChildUser(eq(reportId), eq(user))).thenReturn(Optional.of(report))

        val response = reportService.getReportStatus(reportId, user)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body)
    }

    @Test
    fun `getReportStatus should return false if report text is null`() {
        val reportId = UUID.randomUUID()
        val user = com.misterio.curakid.core.database.auth.User().apply { id = UUID.randomUUID() }
        val report = AiReport(reportText = null).apply { id = reportId }
        whenever(aiReportRepository.findByIdAndChildUser(eq(reportId), eq(user))).thenReturn(Optional.of(report))

        val response = reportService.getReportStatus(reportId, user)

        assertEquals(false, response.body)
    }

    @Test
    fun `preparePeriodReportContext should detect skill regression`() {
        val childId = UUID.randomUUID()
        val periodId = UUID.randomUUID()
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = childId }

        val prevPeriod = LifePeriod(code = "6-12m", minMonths = 6, maxMonths = 12).apply { id = UUID.randomUUID() }
        val currPeriod = LifePeriod(code = "12-18m", minMonths = 12, maxMonths = 18).apply { id = UUID.randomUUID() }

        val summary = PeriodSummary(child = child, lifePeriod = currPeriod).apply { id = periodId }

        val prevAssessment = SkillAssessment(
            child = child, domain = AssessmentDomain.MOTOR_GROSS, lifePeriod = prevPeriod, score = BigDecimal("0.8")
        )
        val currAssessment = SkillAssessment(
            child = child, domain = AssessmentDomain.MOTOR_GROSS, lifePeriod = currPeriod, score = BigDecimal("0.6")
        )

        whenever(childRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(periodSummaryRepository.findById(periodId)).thenReturn(Optional.of(summary))
        whenever(lifePeriodRepository.findAll()).thenReturn(listOf(prevPeriod, currPeriod))
        whenever(lifePeriodRepository.findFirstByMaxMonths(12)).thenReturn(Optional.of(prevPeriod))
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(listOf(currAssessment, prevAssessment))

        val context = reportContextBuilder.preparePeriodReportContext(childId, periodId)
        assertNotNull(context)
        assertEquals(true, context!!.domainScores!!.regressions["MOTOR_GROSS"])
    }

    @Test
    fun `regenerateReport should throw AccessDeniedException and never save when summary belongs to another user`() {
        // Given
        val user = com.misterio.curakid.core.database.auth.User(email = "user@example.com").apply { id = UUID.randomUUID() }
        val otherUser = com.misterio.curakid.core.database.auth.User(email = "other@example.com").apply { id = UUID.randomUUID() }
        val foreignChild = Child(user = otherUser, name = "Foreign Child").apply { id = UUID.randomUUID() }
        val summaryId = UUID.randomUUID()
        val summary = PeriodSummary(child = foreignChild).apply { id = summaryId }

        whenever(periodSummaryRepository.findById(summaryId)).thenReturn(Optional.of(summary))

        // When / Then
        assertThrows(AccessDeniedException::class.java) {
            reportService.regenerateReport(user, summaryId)
        }
        verify(aiReportRepository, never()).save(any())
    }

    @Test
    fun `triggerPeriodReportGeneration should throw NoSuchElementException and never save when child does not belong to user`() {
        // Given
        val user = com.misterio.curakid.core.database.auth.User(email = "user@example.com").apply { id = UUID.randomUUID() }
        val foreignChildId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUser(foreignChildId, user)).thenReturn(Optional.empty())

        // When / Then
        assertThrows(NoSuchElementException::class.java) {
            reportService.triggerPeriodReportGeneration(foreignChildId, user)
        }
        verify(aiReportRepository, never()).save(any())
    }
}
