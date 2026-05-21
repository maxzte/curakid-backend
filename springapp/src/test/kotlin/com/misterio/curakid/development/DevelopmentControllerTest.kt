package com.misterio.curakid.development

import com.misterio.curakid.feature.development.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.util.*

class DevelopmentControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var lifePeriodQuestionService: LifePeriodQuestionService

    @BeforeEach
    fun setUp() {
        lifePeriodQuestionService = mock()
        val controller = DevelopmentController(lifePeriodQuestionService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET life-periods returns 200 with list`() {
        val periods = listOf(
            PeriodResponse(
                periodId = UUID.randomUUID(),
                code = "P1M",
                label = "1 міс",
                description = "lorem ipsum",
                minMonths = 1,
                maxMonths = 1,
                questions = listOf(
                    QuestionResponse(
                        questionId = UUID.randomUUID(),
                        text = "Can the child see?",
                        domain = "MOTOR",
                        weight = BigDecimal("1.0"),
                        options = listOf(
                            OptionResponse(UUID.randomUUID(), "Yes", "1", 1),
                            OptionResponse(UUID.randomUUID(), "No", "0", 2)
                        )
                    )
                )
            )
        )

        whenever(lifePeriodQuestionService.getAllPeriodsWithQuestions()).thenReturn(periods)

        mockMvc.perform(get("/api/development/life-periods"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].code").value("P1M"))
            .andExpect(jsonPath("$[0].label").value("1 міс"))
    }

    @Test
    fun `GET life-periods by code returns 200`() {
        val period = PeriodResponse(
            periodId = UUID.randomUUID(),
            code = "P3_4M",
            label = "3–4 міс",
            description = "lorem ipsum",
            minMonths = 3,
            maxMonths = 4,
            questions = emptyList()
        )

        whenever(lifePeriodQuestionService.getPeriodWithQuestions("P3_4M")).thenReturn(period)

        mockMvc.perform(get("/api/development/life-periods/P3_4M"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("P3_4M"))
    }
}
