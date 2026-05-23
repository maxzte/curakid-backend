package com.misterio.curakid.admin

import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdminQuizService(
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository,
    private val answerOptionRepository: AnswerOptionRepository
) {

    // --- Life Periods ---

    @Transactional(readOnly = true)
    fun getLifePeriods(): List<LifePeriod> {
        val periods = lifePeriodRepository.findAllWithQuestions()
        val questionIds = periods.flatMap { it.questions }.mapNotNull { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }
        return periods
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun createLifePeriod(request: CreateLifePeriodRequest): LifePeriod {
        val period = LifePeriod(
            code = request.code,
            label = request.label,
            description = request.description,
            minMonths = request.minMonths,
            maxMonths = request.maxMonths
        )
        return lifePeriodRepository.save(period)
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun deleteLifePeriod(id: UUID) = lifePeriodRepository.deleteById(id)

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun updateLifePeriod(id: UUID, request: CreateLifePeriodRequest): LifePeriod {
        val existing = lifePeriodRepository.findById(id).orElseThrow {
            IllegalArgumentException("LifePeriod $id not found")
        }
        existing.code = request.code
        existing.label = request.label
        existing.description = request.description
        existing.minMonths = request.minMonths
        existing.maxMonths = request.maxMonths
        return lifePeriodRepository.save(existing)
    }

    // --- Questions ---

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun createQuestion(lifePeriodId: UUID, request: CreateQuizQuestionRequest): Question {
        val period = lifePeriodRepository.findById(lifePeriodId)
            .orElseThrow { IllegalArgumentException("LifePeriod $lifePeriodId not found") }

        val question = Question(
            text = request.text,
            domain = request.domain,
            weight = request.weight,
            lifePeriod = period
        )
        
        request.options.forEach { opt ->
            question.options.add(
                AnswerOption(
                    question = question,
                    text = opt.text,
                    value = opt.value,
                    displayOrder = opt.displayOrder
                )
            )
        }
        
        return questionRepository.save(question)
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun updateQuestion(id: UUID, request: CreateQuizQuestionRequest): Question {
        val existing = questionRepository.findById(id).orElseThrow {
            IllegalArgumentException("Question $id not found")
        }
        existing.text = request.text
        existing.domain = request.domain
        existing.weight = request.weight
        return questionRepository.save(existing)
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun deleteQuestion(id: UUID) {
        val existing = questionRepository.findById(id).orElseThrow {
            IllegalArgumentException("Question $id not found")
        }
        existing.isActive = false
        questionRepository.save(existing)
    }

    // --- Answer Options ---

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun createAnswerOption(questionId: UUID, request: CreateAnswerOptionRequest): AnswerOption {
        val question = questionRepository.findById(questionId)
            .orElseThrow { IllegalArgumentException("Question $questionId not found") }

        val option = AnswerOption(
            question = question,
            text = request.text,
            value = request.value,
            displayOrder = request.displayOrder
        )
        return answerOptionRepository.save(option)
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun deleteAnswerOption(id: UUID) = answerOptionRepository.deleteById(id)

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun updateAnswerOption(id: UUID, request: CreateAnswerOptionRequest): AnswerOption {
        val existing = answerOptionRepository.findById(id).orElseThrow {
            IllegalArgumentException("AnswerOption $id not found")
        }
        existing.text = request.text
        existing.value = request.value
        existing.displayOrder = request.displayOrder
        return answerOptionRepository.save(existing)
    }
}
