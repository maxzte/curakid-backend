package com.misterio.curakid.reports

import com.misterio.curakid.core.essential.common.analysis.IntelligenceResponse
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReport
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.feature.fis.FisClient
import com.misterio.curakid.feature.fis.FisResponse
import com.misterio.curakid.feature.knowledge.KnowledgeService
import com.misterio.curakid.feature.reports.ReportService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.ai.chat.client.ChatClient
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class ReportServiceTest {

    private lateinit var aiReportRepository: AiReportRepository
    private lateinit var periodSummaryRepository: PeriodSummaryRepository
    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var assessmentRepository: SkillAssessmentRepository
    private lateinit var fuzzyRuleRepository: FuzzyRuleRepository
    private lateinit var childRepository: ChildRepository
    private lateinit var fisClient: FisClient
    private lateinit var knowledgeService: KnowledgeService
    private lateinit var chatClient: ChatClient
    private lateinit var reportService: ReportService

    @BeforeEach
    fun setUp() {
        aiReportRepository = mock()
        periodSummaryRepository = mock()
        measurementRepository = mock()
        assessmentRepository = mock()
        chatClient = mock()
        fuzzyRuleRepository = mock()
        fisClient = mock()
        knowledgeService = mock()
        childRepository = mock()

        reportService = ReportService(
            aiReportRepository = aiReportRepository,
            periodSummaryRepository = periodSummaryRepository,
            measurementRepository = measurementRepository,
            assessmentRepository = assessmentRepository,
            intelligenceProvider = fisClient,
            knowledgeProvider = knowledgeService,
            chatClient = chatClient,
            childRepository = childRepository,
            fuzzyRuleRepository = fuzzyRuleRepository,
        )
    }

    @Test
    fun `generateReport should orchestrate full flow`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply {
            id = UUID.randomUUID()
        }

        // Mock DB calls
        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        whenever(fuzzyRuleRepository.findAllByIsActiveTrue()).thenReturn(emptyList())

        // Mock FIS
        val fisResponse = FisResponse(urgencyScore = BigDecimal("0.6"), isFallback = false)
        whenever(fisClient.analyze(any())).thenReturn(fisResponse.toIntelligenceResponse())

        // Mock Knowledge
        whenever(knowledgeService.search(any(), any())).thenReturn(emptyList())

        // Mock SpringAI ChatClient
        val mockPromptSpec: ChatClient.ChatClientRequestSpec = mock()
        val mockCallSpec: ChatClient.CallResponseSpec = mock()
        whenever(chatClient.prompt(any<String>())).thenReturn(mockPromptSpec)
        whenever(mockPromptSpec.call()).thenReturn(mockCallSpec)
        whenever(mockCallSpec.content()).thenReturn("ЗВІТ: Все добре.\nРЕКОМЕНДАЦІЇ: Гуляйте більше.")

        // Mock Save
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        val result = reportService.generateReport(child)

        assertNotNull(result)
        assertEquals(BigDecimal("0.6"), result.urgencyScore)
        assertEquals("Все добре.", result.reportText)
        assertEquals("Гуляйте більше.", result.recommendations)

        verify(fisClient).analyze(any())
        verify(knowledgeService).search(any(), eq(BigDecimal("0.6")))
        verify(aiReportRepository).save(any())
    }

    @Test
    fun `generateReport should handle Gemini failure gracefully`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = UUID.randomUUID() }

        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        whenever(fuzzyRuleRepository.findAllByIsActiveTrue()).thenReturn(emptyList())
        whenever(fisClient.analyze(any())).thenReturn(FisResponse(BigDecimal("0.5")).toIntelligenceResponse())
        whenever(knowledgeService.search(any(), any())).thenReturn(emptyList())

        // Simulate exception in AI call
        whenever(chatClient.prompt(any<String>())).thenThrow(RuntimeException("AI Down"))

        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        val result = reportService.generateReport(child)

        assertNotNull(result)
        assertTrue(result.reportText!!.contains("тимчасово недоступний"))
        verify(aiReportRepository).save(any())
    }

    @Test
    fun `generateEmergencyReport should call generateReport with override`() {
        val childId = UUID.randomUUID()
        val measurementId = UUID.randomUUID()
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = childId }
        val measurement = Measurement(child = child, measurementDate = LocalDateTime.now()).apply { id = measurementId }

        whenever(childRepository.findById(childId)).thenReturn(Optional.of(child))
        whenever(measurementRepository.findById(measurementId)).thenReturn(Optional.of(measurement))

        // Mock the internal generateReport call by mocking all dependencies
        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        whenever(fuzzyRuleRepository.findAllByIsActiveTrue()).thenReturn(emptyList())
        whenever(fisClient.analyze(any())).thenReturn(FisResponse(BigDecimal("0.1")).toIntelligenceResponse())
        whenever(knowledgeService.search(any(), any())).thenReturn(emptyList())
        val mockCallSpec: ChatClient.CallResponseSpec = mock()
        whenever(mockCallSpec.content()).thenReturn("ЗВІТ: OK\nРЕКОМЕНДАЦІЇ: OK")
        val mockPromptSpec: ChatClient.ChatClientRequestSpec = mock()
        whenever(mockPromptSpec.call()).thenReturn(mockCallSpec)
        whenever(chatClient.prompt(any<String>())).thenReturn(mockPromptSpec)
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }
        whenever(aiReportRepository.findById(any())).thenReturn(Optional.of(AiReport(child = child).apply {
            id = UUID.randomUUID()
        }))

        reportService.generateEmergencyReport(childId, measurementId)

        verify(childRepository).findById(childId)
        verify(measurementRepository).findById(measurementId)
        verify(aiReportRepository).save(argThat { triggerMeasurement?.id == measurementId })
    }

    @Test
    fun `generateReport should use critical level for urgency score 0_75`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        whenever(fuzzyRuleRepository.findAllByIsActiveTrue()).thenReturn(emptyList())

        // Urgency score 0.75 exactly
        val intelligenceResponse = IntelligenceResponse(
            urgencyScore = BigDecimal("0.75"),
            breakdown = emptyMap(),
            isFallback = false
        )
        whenever(fisClient.analyze(any())).thenReturn(intelligenceResponse)
        whenever(knowledgeService.search(any(), any())).thenReturn(emptyList())
        val mockCallSpec: ChatClient.CallResponseSpec = mock()
        whenever(mockCallSpec.content()).thenReturn("ЗВІТ: OK\nРЕКОМЕНДАЦІЇ: OK")
        val mockPromptSpec: ChatClient.ChatClientRequestSpec = mock()
        whenever(mockPromptSpec.call()).thenReturn(mockCallSpec)
        whenever(chatClient.prompt(any<String>())).thenReturn(mockPromptSpec)
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        reportService.generateReport(child)

        // Verify "критичний стан" was in the search query
        verify(knowledgeService).search(argThat { contains("критичний стан") }, eq(BigDecimal("0.75")))
    }

    @Test
    fun `generateReport should use moderate level for urgency score 0_5`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        whenever(fuzzyRuleRepository.findAllByIsActiveTrue()).thenReturn(emptyList())

        // Urgency score 0.5 exactly
        val intelligenceResponse = IntelligenceResponse(
            urgencyScore = BigDecimal("0.5"),
            breakdown = emptyMap(),
            isFallback = false
        )
        whenever(fisClient.analyze(any())).thenReturn(intelligenceResponse)
        whenever(knowledgeService.search(any(), any())).thenReturn(emptyList())
        val mockCallSpec: ChatClient.CallResponseSpec = mock()
        whenever(mockCallSpec.content()).thenReturn("ЗВІТ: OK\nРЕКОМЕНДАЦІЇ: OK")
        val mockPromptSpec: ChatClient.ChatClientRequestSpec = mock()
        whenever(mockPromptSpec.call()).thenReturn(mockCallSpec)
        whenever(chatClient.prompt(any<String>())).thenReturn(mockPromptSpec)
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        reportService.generateReport(child)

        verify(knowledgeService).search(argThat { contains("помірна тривожність") }, eq(BigDecimal("0.5")))
    }

    @Test
    fun `parseGeminiResponse should handle missing sections gracefully`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        whenever(fuzzyRuleRepository.findAllByIsActiveTrue()).thenReturn(emptyList())
        val intelligenceResponse = IntelligenceResponse(
            urgencyScore = BigDecimal("0.1"),
            breakdown = emptyMap(),
            isFallback = false
        )
        whenever(fisClient.analyze(any())).thenReturn(intelligenceResponse)
        whenever(knowledgeService.search(any(), any())).thenReturn(emptyList())

        // No "РЕКОМЕНДАЦІЇ" section
        val mockCallSpec: ChatClient.CallResponseSpec = mock()
        whenever(mockCallSpec.content()).thenReturn("Тут просто якийсь текст без заголовків.")
        val mockPromptSpec: ChatClient.ChatClientRequestSpec = mock()
        whenever(mockPromptSpec.call()).thenReturn(mockCallSpec)
        whenever(chatClient.prompt(any<String>())).thenReturn(mockPromptSpec)
        whenever(aiReportRepository.save(any())).thenAnswer {
            (it.arguments[0] as AiReport).apply { id = UUID.randomUUID() }
        }

        val result = reportService.generateReport(child)

        // It should take the whole text as reportText and empty recommendations
        assertEquals("Тут просто якийсь текст без заголовків.", result.reportText)
        assertEquals("", result.recommendations)
    }
}

private fun FisResponse.toIntelligenceResponse() = IntelligenceResponse(
    urgencyScore = urgencyScore,
    breakdown = breakdown,
    isFallback = isFallback
)