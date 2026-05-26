package com.misterio.curakid.reports

import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReport
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeProvider
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import com.misterio.curakid.feature.reports.ReportService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class ReportServiceTest {

    private lateinit var aiReportRepository: AiReportRepository
    private lateinit var periodSummaryRepository: PeriodSummaryRepository
    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var assessmentRepository: SkillAssessmentRepository
    private lateinit var fuzzyRuleRepository: FuzzyRuleRepository
    private lateinit var childRepository: ChildRepository
    private lateinit var intelligenceProvider: IntelligenceProvider
    private lateinit var knowledgeProvider: KnowledgeProvider
    private lateinit var chatClient: ChatClient
    private lateinit var percentileCalculator: WhoPercentileCalculator
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var reportService: ReportService

    @BeforeEach
    fun setUp() {
        aiReportRepository = mock()
        periodSummaryRepository = mock()
        measurementRepository = mock()
        assessmentRepository = mock()
        chatClient = mock()
        fuzzyRuleRepository = mock()
        intelligenceProvider = mock()
        knowledgeProvider = mock()
        childRepository = mock()
        percentileCalculator = mock()
        transactionTemplate = mock()

        // Mock transaction template to execute the block immediately
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer {
            (it.arguments[0] as TransactionCallback<Any>).doInTransaction(mock<TransactionStatus>())
        }

        reportService = ReportService(
            aiReportRepository = aiReportRepository,
            periodSummaryRepository = periodSummaryRepository,
            measurementRepository = measurementRepository,
            assessmentRepository = assessmentRepository,
            intelligenceProvider = intelligenceProvider,
            knowledgeProvider = knowledgeProvider,
            chatClient = chatClient,
            childRepository = childRepository,
            fuzzyRuleRepository = fuzzyRuleRepository,
            percentileCalculator = percentileCalculator,
            transactionTemplate = transactionTemplate
        )
    }

    @Test
    fun `generateEmergencyReport should create placeholder and return its ID`() {
        val childId = UUID.randomUUID()
        val measurementId = UUID.randomUUID()
        val child =
            Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = childId }
        val measurement = Measurement(child = child, measurementDate = Instant.now()).apply {
            id = measurementId
        }

        whenever(childRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(measurementRepository.findById(measurementId)).thenReturn(Optional.of(measurement))
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        val reportId = reportService.generateEmergencyReport(childId, measurementId)

        assertNotNull(reportId)
        verify(
            aiReportRepository,
            atLeastOnce()
        ).save(any())
    }

    @Test
    fun `generatePeriodReport should create placeholder and return its ID`() {
        val childId = UUID.randomUUID()
        val periodId = UUID.randomUUID()
        val child =
            Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = childId }
        val summary = com.misterio.curakid.core.database.reports.PeriodSummary(child = child)
            .apply { id = periodId }

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
        val report = AiReport(reportText = "Done").apply { id = reportId }
        whenever(aiReportRepository.findById(reportId)).thenReturn(Optional.of(report))

        val response = reportService.getReportStatus(reportId)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body)
    }

    @Test
    fun `getReportStatus should return false if report text is null`() {
        val reportId = UUID.randomUUID()
        val report = AiReport(reportText = null).apply { id = reportId }
        whenever(aiReportRepository.findById(reportId)).thenReturn(Optional.of(report))

        val response = reportService.getReportStatus(reportId)

        assertEquals(false, response.body)
    }

    @Test
    fun `buildSearchQuery should use correct template based on urgency score`() {
        val buildSearchQueryMethod = reportService.javaClass.getDeclaredMethod(
            "buildSearchQuery",
            com.misterio.curakid.core.database.children.Gender::class.java,
            Int::class.java,
            Int::class.java,
            java.math.BigDecimal::class.java,
            List::class.java
        ).apply { isAccessible = true }

        // Test low urgency
        val lowUrgencyQuery = buildSearchQueryMethod.invoke(
            reportService,
            com.misterio.curakid.core.database.children.Gender.MALE,
            12,
            5,
            java.math.BigDecimal("0.5"),
            listOf("Норма ваги: 0.9")
        ) as String
        assertEquals("загальний розвиток та вправи, Норма ваги: 0.9, MALE, вік 12 міс. та 5 дн.", lowUrgencyQuery)

        // Test high urgency
        val highUrgencyQuery = buildSearchQueryMethod.invoke(
            reportService,
            com.misterio.curakid.core.database.children.Gender.FEMALE,
            24,
            0,
            java.math.BigDecimal("0.8"),
            listOf("Затримка моторики: 0.8")
        ) as String
        assertEquals("критичні відхилення та патології, Затримка моторики: 0.8, FEMALE, вік 24 міс. та 0 дн.", highUrgencyQuery)
    }
}
