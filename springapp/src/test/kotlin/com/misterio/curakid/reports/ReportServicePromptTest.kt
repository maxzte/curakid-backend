package com.misterio.curakid.reports

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.feature.reports.ReportService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.math.BigDecimal

class ReportServicePromptTest {

    private lateinit var reportService: ReportService

    @BeforeEach
    fun setUp() {
        reportService = ReportService(
            aiReportRepository = mock(),
            periodSummaryRepository = mock(),
            measurementRepository = mock(),
            assessmentRepository = mock(),
            lifePeriodRepository = mock(),
            intelligenceProvider = mock(),
            knowledgeProvider = mock(),
            chatClient = mock(),
            childRepository = mock(),
            fuzzyRuleRepository = mock(),
            percentileCalculator = mock(),
            transactionTemplate = mock(),
            measurementCalculator = MeasurementCalculator()
        )
    }

    @Test
    fun `buildPrompt should contain updated role and rules`() {
        val buildPromptMethod = reportService.javaClass.getDeclaredMethod(
            "buildPrompt",
            Class.forName("com.misterio.curakid.feature.reports.ChildData"),
            Int::class.java,
            Int::class.java,
            Class.forName("com.misterio.curakid.feature.reports.MeasurementSummary"),
            Class.forName("com.misterio.curakid.feature.reports.DomainScores"),
            BigDecimal::class.java,
            List::class.java,
            Map::class.java
        ).apply { isAccessible = true }

        val childDataClass = Class.forName("com.misterio.curakid.feature.reports.ChildData")
        val childDataConstructor = childDataClass.getDeclaredConstructor(
            Gender::class.java, Boolean::class.java, List::class.java, List::class.java, List::class.java, List::class.java
        ).apply { isAccessible = true }
        val childData = childDataConstructor.newInstance(Gender.MALE, false, emptyList<String>(), emptyList<String>(), emptyList<String>(), emptyList<String>())

        val prompt = buildPromptMethod.invoke(
            reportService,
            childData,
            12, // ageMonths
            0,  // ageDays
            null, // latestMeasurement
            null, // domainScores
            BigDecimal("0.5"), // urgencyScore
            emptyList<Any>(), // chunks
            emptyMap<String, Any>() // norms
        ) as String

        // Verify Role
        assertTrue(prompt.contains("Ти — турботливий та висококваліфікований педіатр"))
        assertTrue(prompt.contains("Тон має бути підтримуючим та конструктивним"))

        // Verify Hard Rules
        assertTrue(prompt.contains("Звертайся до батьків у привітній та професійній манері"))
        assertTrue(prompt.contains("НЕ згадуй цифри"))
        assertTrue(prompt.contains("ОБОВ’ЯЗКОВО зверни на це увагу батьків"))

        // Verify Sections from Const.kt
        assertTrue(prompt.contains("Спілкуйся з батьками як партнер"))
        assertTrue(prompt.contains("ОБОВ'ЯЗКОВО рекомендуй звернутися до лікаря"))
    }
}
