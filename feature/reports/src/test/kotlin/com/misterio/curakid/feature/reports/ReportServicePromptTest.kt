package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.essential.common.analysis.SearchConfidence
import com.misterio.curakid.core.medical.MeasurementCalculator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServicePromptTest {

    @Mock lateinit var aiReportRepository: com.misterio.curakid.core.database.reports.AiReportRepository
    @Mock lateinit var periodSummaryRepository: com.misterio.curakid.core.database.reports.PeriodSummaryRepository
    @Mock lateinit var measurementRepository: com.misterio.curakid.core.database.measurements.MeasurementRepository
    @Mock lateinit var assessmentRepository: com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
    @Mock lateinit var lifePeriodRepository: com.misterio.curakid.core.database.development.LifePeriodRepository
    @Mock lateinit var intelligenceProvider: com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
    @Mock lateinit var knowledgeProvider: com.misterio.curakid.core.essential.common.analysis.KnowledgeProvider
    @Mock lateinit var chatClient: ChatClient
    @Mock lateinit var childRepository: com.misterio.curakid.core.database.children.ChildRepository
    @Mock lateinit var fuzzyRuleRepository: com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
    @Mock lateinit var percentileCalculator: com.misterio.curakid.core.medical.WhoPercentileCalculator
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
            measurementCalculator = measurementCalculator
        )
    }

    @Test
    fun `buildPrompt should contain updated role and rules`() {
        val childData = ChildData(
            gender = Gender.MALE,
            isPremature = false,
            allergies = emptyList(),
            chronicConditions = emptyList(),
            pastIllnesses = emptyList(),
            notes = emptyList()
        )

        val prompt = reportService.buildPrompt(
            childData = childData,
            ageMonths = 12,
            ageDays = 0,
            latestMeasurement = null,
            domainScores = null,
            urgencyScore = BigDecimal("0.5"),
            chunks = emptyList(),
            confidence = SearchConfidence.HIGH,
            norms = emptyMap()
        )

        assertTrue(prompt.contains("Ти — турботливий та висококваліфікований педіатр"))
        assertTrue(prompt.contains("Тон має бути підтримуючим та конструктивним"))
        assertTrue(prompt.contains("Звертайся до батьків у привітній та професійній манері"))
        assertTrue(prompt.contains("НЕ згадуй цифри"))
        assertTrue(prompt.contains("ОБОВ\u2019ЯЗКОВО зверни на це увагу батьків"))
        assertTrue(prompt.contains("Спілкуйся з батьками як партнер"))
        assertTrue(prompt.contains("ОБОВ'ЯЗКОВО рекомендуй звернутися до лікаря"))
    }
}
