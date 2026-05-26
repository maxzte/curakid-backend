package com.misterio.curakid.admin

import com.misterio.curakid.admin.service.AdminQuizService
import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class AdminQuizServiceTest {

    private lateinit var lifePeriodRepository: LifePeriodRepository
    private lateinit var questionRepository: QuestionRepository
    private lateinit var answerOptionRepository: AnswerOptionRepository
    private lateinit var service: AdminQuizService

    @BeforeEach
    fun setUp() {
        lifePeriodRepository = mock()
        questionRepository = mock()
        answerOptionRepository = mock()
        service = AdminQuizService(lifePeriodRepository, questionRepository, answerOptionRepository)
    }

    // --- Life Period ---

    @Test
    fun `createLifePeriod should save and return period`() {
        val request = CreateLifePeriodRequest(
            code = "P1M",
            label = "1 міс",
            description = "lorem ipsum",
            minMonths = 1,
            maxMonths = 1
        )
        whenever(lifePeriodRepository.save(any<LifePeriod>())).thenAnswer { it.arguments[0] }

        val result = service.createLifePeriod(request)

        assertEquals("P1M", result.code)
        assertEquals("1 міс", result.label)
        assertEquals(1, result.minMonths)
        assertEquals(1, result.maxMonths)
        verify(lifePeriodRepository).save(any())
    }

    @Test
    fun `deleteLifePeriod should call repository`() {
        val id = UUID.randomUUID()

        service.deleteLifePeriod(id)

        verify(lifePeriodRepository).deleteById(id)
    }

    @Test
    fun `getLifePeriods should delegate to repository`() {
        whenever(lifePeriodRepository.findAllWithQuestions()).thenReturn(emptyList())

        val result = service.getLifePeriods()

        assertTrue(result.isEmpty())
        verify(lifePeriodRepository).findAllWithQuestions()
    }

    // --- Questions ---

    @Test
    fun `createQuestion should link to correct life period`() {
        val periodId = UUID.randomUUID()
        val period =
            LifePeriod(
                code = "P1M",
                label = "1 міс",
                description = "lorem ipsum",
                minMonths = 1,
                maxMonths = 1
            ).apply {
                id = periodId
            }
        val request = CreateQuizQuestionRequest(
            "Does the child respond?",
            AssessmentDomain.MOTOR_GROSS,
            BigDecimal("1.0")
        )

        whenever(lifePeriodRepository.findById(periodId)).thenReturn(Optional.of(period))
        whenever(questionRepository.save(any<Question>())).thenAnswer { it.arguments[0] }

        val result = service.createQuestion(periodId, request)

        assertEquals("Does the child respond?", result.text)
        assertEquals(AssessmentDomain.MOTOR_GROSS, result.domain)
        assertEquals(period, result.lifePeriod)
        verify(questionRepository).save(any())
    }

    @Test
    fun `createQuestion should throw when period not found`() {
        val periodId = UUID.randomUUID()
        val request =
            CreateQuizQuestionRequest("text", AssessmentDomain.MOTOR_GROSS, BigDecimal("1.0"))

        whenever(lifePeriodRepository.findById(periodId)).thenReturn(Optional.empty())

        assertThrows<IllegalArgumentException> {
            service.createQuestion(periodId, request)
        }
    }

    @Test
    fun `updateQuestion should update existing fields`() {
        val questionId = UUID.randomUUID()
        val period =
            LifePeriod(
                code = "P1M",
                label = "1 міс",
                description = "lorem ipsum",
                minMonths = 1,
                maxMonths = 1
            )
        val existing = Question(
            "Old Text",
            AssessmentDomain.MOTOR_GROSS,
            BigDecimal("1.0"),
            period
        ).apply { id = questionId }
        val request =
            CreateQuizQuestionRequest("New Text", AssessmentDomain.SPEECH, BigDecimal("1.5"))

        whenever(questionRepository.findById(questionId)).thenReturn(Optional.of(existing))
        whenever(questionRepository.save(any<Question>())).thenAnswer { it.arguments[0] }

        val result = service.updateQuestion(questionId, request)

        assertEquals("New Text", result.text)
        assertEquals(AssessmentDomain.SPEECH, result.domain)
        assertEquals(BigDecimal("1.5"), result.weight)
        verify(questionRepository).save(existing)
    }

    @Test
    fun `deleteQuestion should call repository`() {
        val id = UUID.randomUUID()
        val question = Question().apply { this.id = id }
        whenever(questionRepository.findById(id)).thenReturn(Optional.of(question))

        service.deleteQuestion(id)

        assertFalse(question.isActive)
        verify(questionRepository).save(question)
    }

    // --- Answer Options ---

    @Test
    fun `createAnswerOption should link to correct question`() {
        val questionId = UUID.randomUUID()
        val period =
            LifePeriod(
                code = "P1M",
                label = "1 міс",
                description = "lorem ipsum",
                minMonths = 1,
                maxMonths = 1
            )
        val question =
            Question("text", AssessmentDomain.MOTOR_GROSS, BigDecimal("1.0"), period).apply {
                id = questionId
            }
        val request = CreateAnswerOptionRequest("Yes", 1.0, 1)

        whenever(questionRepository.findById(questionId)).thenReturn(Optional.of(question))
        whenever(answerOptionRepository.save(any<AnswerOption>())).thenAnswer { it.arguments[0] }

        val result = service.createAnswerOption(questionId, request)

        assertEquals("Yes", result.text)
        assertEquals(1.0, result.value)
        assertEquals(1, result.displayOrder)
        assertEquals(question, result.question)
        verify(answerOptionRepository).save(any())
    }

    @Test
    fun `createAnswerOption should throw when question not found`() {
        val questionId = UUID.randomUUID()
        val request = CreateAnswerOptionRequest("Yes", 1.0, 1)

        whenever(questionRepository.findById(questionId)).thenReturn(Optional.empty())

        assertThrows<IllegalArgumentException> {
            service.createAnswerOption(questionId, request)
        }
    }

    @Test
    fun `deleteAnswerOption should call repository`() {
        val id = UUID.randomUUID()

        service.deleteAnswerOption(id)

        verify(answerOptionRepository).deleteById(id)
    }
}
