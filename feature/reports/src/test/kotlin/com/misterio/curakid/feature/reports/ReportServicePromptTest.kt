package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeProvider
import com.misterio.curakid.core.essential.common.analysis.SearchConfidence
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.springframework.ai.chat.client.ChatClient
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServicePromptTest {

    @Mock lateinit var aiReportRepository: AiReportRepository
    @Mock lateinit var periodSummaryRepository: PeriodSummaryRepository
    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var assessmentRepository: SkillAssessmentRepository
    @Mock lateinit var lifePeriodRepository: LifePeriodRepository
    @Mock lateinit var intelligenceProvider: IntelligenceProvider
    @Mock lateinit var knowledgeProvider: KnowledgeProvider
    @Mock lateinit var chatClient: ChatClient
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var fuzzyRuleRepository: FuzzyRuleRepository
    @Mock lateinit var percentileCalculator: WhoPercentileCalculator
    @Mock lateinit var transactionManager: PlatformTransactionManager
    @Mock lateinit var eventPublisher: ApplicationEventPublisher

    private val measurementCalculator = MeasurementCalculator()
    private val promptBuilder = ReportPromptBuilder()
    private lateinit var reportService: ReportService

    @BeforeEach
    fun setUp() {
        val reportContextBuilder = ReportContextBuilder(
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

        val reportGenerationEngine = ReportGenerationEngine(
            intelligenceProvider = intelligenceProvider,
            knowledgeProvider = knowledgeProvider,
            chatClient = chatClient,
            promptBuilder = promptBuilder,
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
            taskExecutor = SyncTaskExecutor(),
            eventPublisher = eventPublisher,
            reportContextBuilder = reportContextBuilder,
            reportGenerationEngine = reportGenerationEngine
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

        val prompt = promptBuilder.buildPrompt(
            childData = childData,
            ageMonths = 12,
            ageDays = 0,
            latestMeasurement = null,
            domainScores = null,
            urgencyScore = BigDecimal("0.5"),
            chunks = emptyList(),
            confidence = SearchConfidence.HIGH,
            norms = emptyMap<String, Map<String, Double>>()
        )

        assertTrue(prompt.contains("Ти — турботливий та висококваліфікований педіатр"))
        assertTrue(prompt.contains("Тон має бути підтримуючим та конструктивним"))
        assertTrue(prompt.contains("Звертайся до батьків у привітній та професійній манері"))
        assertTrue(prompt.contains("НЕ згадуй цифри"))
        assertTrue(prompt.contains("ОБОВ\u2019ЯЗКОВО зверни на це увагу батьків"))
        assertTrue(prompt.contains("Спілкуйся з батьками як партнер"))
        assertTrue(prompt.contains("ОБОВ'ЯЗКОВО рекомендуй звернутися до лікаря"))
    }

    @Test
    fun `buildPrompt should include skills section when only cognitive or social domain present`() {
        val childData = ChildData(
            gender = Gender.FEMALE,
            isPremature = false,
            allergies = emptyList(),
            chronicConditions = emptyList(),
            pastIllnesses = emptyList(),
            notes = emptyList()
        )

        val domainScores = DomainScores(
            cognitive = BigDecimal("85.0"),
            social = BigDecimal("90.0"),
            motorGross = null,
            motorFine = null,
            speech = null,
            selfCare = null,
            sensory = null
        )

        val prompt = promptBuilder.buildPrompt(
            childData = childData,
            ageMonths = 18,
            ageDays = 0,
            latestMeasurement = null,
            domainScores = domainScores,
            urgencyScore = BigDecimal("0.1"),
            chunks = emptyList(),
            confidence = SearchConfidence.HIGH,
            norms = emptyMap<String, Map<String, Double>>()
        )

        assertTrue(prompt.contains("ОЦІНКА НАВИЧОК"), "Skills section must be rendered")
        assertTrue(prompt.contains("Когніція: 85.0"))
        assertTrue(prompt.contains("Соціалізація: 90.0"))
        assertTrue(!prompt.contains("Дані щодо розвитку навичок наразі відсутні"))
    }
}
