package com.misterio.curakid.reports

import com.misterio.curakid.assessments.SkillAssessmentRepository
import com.misterio.curakid.children.Child
import com.misterio.curakid.children.Gender
import com.misterio.curakid.fis.FisClient
import com.misterio.curakid.fis.FisResponse
import com.misterio.curakid.knowledge.KnowledgeService
import com.misterio.curakid.measurements.MeasurementRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.ai.chat.client.ChatClient
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class ReportServiceTest {

    private lateinit var aiReportRepository: AiReportRepository
    private lateinit var periodSummaryRepository: PeriodSummaryRepository
    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var assessmentRepository: SkillAssessmentRepository
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
        fisClient = mock()
        knowledgeService = mock()
        chatClient = mock()
        
        reportService = ReportService(
            aiReportRepository, periodSummaryRepository, measurementRepository,
            assessmentRepository, fisClient, knowledgeService, chatClient
        )
    }

    @Test
    fun `generateReport should orchestrate full flow`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        
        // Mock DB calls
        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        
        // Mock FIS
        val fisResponse = FisResponse(urgencyScore = BigDecimal("0.6"), isFallback = false)
        whenever(fisClient.analyze(any())).thenReturn(fisResponse)
        
        // Mock Knowledge
        whenever(knowledgeService.search(any(), any(), any())).thenReturn(emptyList())
        
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
        verify(knowledgeService).search(any(), eq(BigDecimal("0.6")), any())
        verify(aiReportRepository).save(any())
    }

    @Test
    fun `generateReport should handle Gemini failure gracefully`() {
        val child = Child(name = "Ivan", birthDate = LocalDate.now().minusMonths(12)).apply { id = UUID.randomUUID() }
        
        whenever(measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)).thenReturn(emptyList())
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())
        whenever(fisClient.analyze(any())).thenReturn(FisResponse(BigDecimal("0.5")))
        whenever(knowledgeService.search(any(), any(), any())).thenReturn(emptyList())
        
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
}
