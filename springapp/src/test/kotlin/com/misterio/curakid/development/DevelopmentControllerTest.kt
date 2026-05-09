package com.misterio.curakid.development

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
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
            PeriodDTO(
                id = UUID.randomUUID(),
                code = "P1M",
                label = "1 міс",
                about = "lorem ipsum",
                minMonths = 1,
                maxMonths = 1,
                questions = listOf(
                    QuestionDTO(
                        id = UUID.randomUUID(),
                        text = "Can the child see?",
                        domain = "MOTOR",
                        weight = BigDecimal("1.0"),
                        options = listOf(
                            OptionDTO(UUID.randomUUID(), "Yes", "1"),
                            OptionDTO(UUID.randomUUID(), "No", "0")
                        )
                    )
                )
            )
        )

        whenever(lifePeriodQuestionService.getAllPeriodsWithQuestions()).thenReturn(periods)

        mockMvc.perform(get("/api/quiz/life-periods"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].code").value("P1M"))
            .andExpect(jsonPath("$[0].label").value("1 міс"))
    }

    @Test
    fun `GET life-periods by code returns 200`() {
        val period = PeriodDTO(
            id = UUID.randomUUID(),
            code = "P3_4M",
            label = "3–4 міс",
            about = "lorem ipsum",
            minMonths = 3,
            maxMonths = 4,
            questions = emptyList()
        )

        whenever(lifePeriodQuestionService.getPeriodWithQuestions("P3_4M")).thenReturn(period)

        mockMvc.perform(get("/api/quiz/life-periods/P3_4M"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.code").value("P3_4M"))
    }
}
