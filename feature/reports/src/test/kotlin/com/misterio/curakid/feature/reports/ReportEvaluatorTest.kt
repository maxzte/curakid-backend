package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeChunkDto
import com.misterio.curakid.core.essential.common.analysis.ReportEvaluationRequestDto
import com.misterio.curakid.core.essential.common.analysis.ReportEvaluationResponseDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.math.BigDecimal
import java.util.UUID

class ReportEvaluatorTest {

    private lateinit var intelligenceProvider: IntelligenceProvider
    private lateinit var aiReportRepository: AiReportRepository
    private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var evaluator: ReportEvaluator

    @BeforeEach
    fun setUp() {
        intelligenceProvider = mock()
        aiReportRepository = mock()
        transactionManager = mock()

        evaluator = ReportEvaluator(intelligenceProvider, aiReportRepository, transactionManager)
    }

    @Test
    fun `verifyReport formats child context correctly and delegates to intelligenceProvider`() {
        // Arrange
        val childContext = ReportContext(
            childId = UUID.randomUUID(),
            ageMonths = 12,
            ageDays = 365,
            childData = ChildData(
                gender = Gender.FEMALE,
                isPremature = false,
                allergies = listOf("None"),
                chronicConditions = listOf("None"),
                pastIllnesses = listOf("Cold"),
                notes = listOf("Active baby")
            ),
            latestMeasurement = MeasurementSummary(
                wfaZscore = BigDecimal("0.2"),
                hfaZscore = BigDecimal("0.1"),
                bmiZscore = BigDecimal("0.15"),
                hcfaZscore = null,
                wfhZscore = null
            ),
            domainScores = null,
            rules = emptyList(),
            periodSummaryId = null,
            overrideMeasurementId = null
        )

        val chunks = listOf(
            KnowledgeChunkDto(title = "Vaccination Guide", content = "Guideline content", relevanceScore = 0.95, isSafeAndApplicable = true)
        )
        val draft = GeneratedReport(
            urgencyScore = BigDecimal("0.1"),
            reportText = "Child is developing well according to WHO charts.",
            recommendations = "Continue routine checkups",
            isFallback = false
        )

        val expectedResponse = ReportEvaluationResponseDto(
            passedGuardrail = true,
            faithfulnessScore = 0.95,
            relevanceScore = 0.92,
            violationReasons = emptyList()
        )
        whenever(intelligenceProvider.evaluateReport(any())).thenReturn(expectedResponse)

        // Act
        val result = evaluator.verifyReport(childContext, chunks, draft)

        // Assert
        assertTrue(result.passedGuardrail)
        assertEquals(0.95, result.faithfulnessScore)
        assertEquals(0.92, result.relevanceScore)

        val requestCaptor = argumentCaptor<ReportEvaluationRequestDto>()
        verify(intelligenceProvider).evaluateReport(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertEquals(draft.reportText, capturedRequest.reportText)
        assertEquals(draft.recommendations, capturedRequest.recommendations)
        assertEquals(12, capturedRequest.childContext["ageMonths"])
        assertEquals("FEMALE", capturedRequest.childContext["gender"])
        assertEquals(BigDecimal("0.2"), capturedRequest.childContext["wfaZscore"])
        assertEquals(BigDecimal("0.1"), capturedRequest.childContext["hfaZscore"])
        assertEquals(BigDecimal("0.15"), capturedRequest.childContext["bmiZscore"])
    }

    @Test
    fun `saveEvaluationScores invokes repository update within transaction`() {
        // Arrange
        val reportId = UUID.randomUUID()
        val faithfulness = 0.88
        val relevance = 0.94

        // Act
        evaluator.saveEvaluationScores(reportId, faithfulness, relevance)

        // Assert
        verify(aiReportRepository).updateEvaluationScores(reportId, faithfulness, relevance)
    }
}
