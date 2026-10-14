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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
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
    @Mock lateinit var transactionTemplate: TransactionTemplate

    private val measurementCalculator = MeasurementCalculator()
    private lateinit var reportService: ReportService

    @BeforeEach
    fun setUp() {
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer {
            (it.arguments[0] as TransactionCallback<Any>).doInTransaction(mock<TransactionStatus>())
        }

        reportService = ReportService(
            aiReportRepository = aiReportRepository,
            periodSummaryRepository = periodSummaryRepository,
            measurementRepository = measurementRepository,
            assessmentRepository = assessmentRepository,
            lifePeriodRepository = lifePeriodRepository,
            intelligenceProvider = intelligenceProvider,
            knowledgeProvider = knowledgeProvider,
            chatClient = chatClient,
            childRepository = childRepository,
            fuzzyRuleRepository = fuzzyRuleRepository,
            percentileCalculator = percentileCalculator,
            transactionTemplate = transactionTemplate,
            measurementCalculator = measurementCalculator,
            taskExecutor = org.springframework.core.task.SyncTaskExecutor()
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
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(listOf(currAssessment, prevAssessment))

        val context = reportService.preparePeriodReportContext(childId, periodId)
        assertNotNull(context)
        assertEquals(true, context!!.domainScores!!.regressions["MOTOR_GROSS"])
    }
}
