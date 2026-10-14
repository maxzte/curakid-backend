package com.misterio.curakid.feature.development

import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import tools.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class LifePeriodQuestionServiceTest {

    @Mock lateinit var lifePeriodRepository: LifePeriodRepository
    @Mock lateinit var questionRepository: QuestionRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var service: LifePeriodQuestionService

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()
        service = LifePeriodQuestionService(lifePeriodRepository, questionRepository, objectMapper)
    }

    @Test
    fun `getAllPeriodsWithQuestions should return sorted DTOs`() {
        val period1 = createPeriod("P1M", "1 міс", about = "lorem ipsum", 1, 1)
        val period2 = createPeriod("P2M", "2 міс", about = "lorem ipsum", 2, 2)

        whenever(lifePeriodRepository.findAllWithQuestions())
            .thenReturn(listOf(period1, period2))

        val result = service.getAllPeriodsWithQuestions()

        assertEquals(2, result.size)
        assertEquals("P1M", result[0].code)
        assertEquals("P2M", result[1].code)
    }

    @Test
    fun `getAllPeriodsWithQuestions should map questions and options correctly`() {
        val period = createPeriod("P1M", "1 міс", "lorem ipsum", 1, 1)
        val question = Question(
            "Can the child see?",
            AssessmentDomain.MOTOR_GROSS,
            BigDecimal("2.0"),
            period
        ).apply {
            id = UUID.randomUUID()
        }
        val option1 = AnswerOption(question, "Yes", 1.0, 1).apply { id = UUID.randomUUID() }
        val option2 = AnswerOption(question, "No", 0.0, 2).apply { id = UUID.randomUUID() }
        question.options.addAll(listOf(option2, option1)) // intentionally unordered
        period.questions.add(question)

        whenever(lifePeriodRepository.findAllWithQuestions())
            .thenReturn(listOf(period))

        val result = service.getAllPeriodsWithQuestions()

        assertEquals(1, result.size)
        val periodDto = result[0]
        assertEquals(1, periodDto.questions.size)

        val questionDto = periodDto.questions[0]
        assertEquals("Can the child see?", questionDto.text)
        assertEquals("MOTOR_GROSS", questionDto.domain)
        assertEquals(2, questionDto.options.size)
        // Options should be sorted by order
        assertEquals("Yes", questionDto.options[0].text)
        assertEquals("No", questionDto.options[1].text)
    }

    @Test
    fun `getAllPeriodsWithQuestions should return empty list when no periods`() {
        whenever(lifePeriodRepository.findAllWithQuestions())
            .thenReturn(emptyList())

        val result = service.getAllPeriodsWithQuestions()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPeriodWithQuestions should return correct DTO`() {
        val period = createPeriod("P3_4M", "3–4 міс", "lorem ipsum", 3, 4)

        whenever(lifePeriodRepository.findByCodeWithQuestions("P3_4M"))
            .thenReturn(Optional.of(period))

        val result = service.getPeriodWithQuestions("P3_4M")

        assertEquals("P3_4M", result.code)
        assertEquals("3–4 міс", result.label)
        assertEquals(3, result.minMonths)
        assertEquals(4, result.maxMonths)
    }

    @Test
    fun `getPeriodWithQuestions should throw for unknown code`() {
        whenever(lifePeriodRepository.findByCodeWithQuestions("UNKNOWN"))
            .thenReturn(Optional.empty())

        assertThrows<IllegalArgumentException> {
            service.getPeriodWithQuestions("UNKNOWN")
        }
    }

    @Test
    fun `questions should be sorted by weight`() {
        val period = createPeriod("P1M", "1 міс", "lorem ipsum", 1, 1)

        val q1 =
            Question("Heavy question", AssessmentDomain.SPEECH, BigDecimal("3.0"), period).apply {
                id = UUID.randomUUID()
            }
        val q2 = Question(
            "Light question",
            AssessmentDomain.MOTOR_GROSS,
            BigDecimal("1.0"),
            period
        ).apply {
            id = UUID.randomUUID()
        }
        period.questions.addAll(listOf(q1, q2)) // heavy first

        whenever(lifePeriodRepository.findAllWithQuestions())
            .thenReturn(listOf(period))

        val result = service.getAllPeriodsWithQuestions()
        val questions = result[0].questions

        assertEquals("Light question", questions[0].text)
        assertEquals("Heavy question", questions[1].text)
    }

    private fun createPeriod(
        code: String,
        label: String,
        about: String,
        min: Int,
        max: Int
    ): LifePeriod {
        return LifePeriod(code, label, about, min, max).apply {
            id = UUID.randomUUID()
        }
    }
}
