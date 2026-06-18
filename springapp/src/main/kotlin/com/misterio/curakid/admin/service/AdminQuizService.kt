package com.misterio.curakid.admin.service

import com.misterio.curakid.admin.CreateAnswerOptionRequest
import com.misterio.curakid.admin.CreateLifePeriodRequest
import com.misterio.curakid.admin.CreateQuizQuestionRequest
import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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
    fun deleteLifePeriod(id: UUID) {
        val existing = lifePeriodRepository.findById(id).orElseThrow {
            IllegalArgumentException("LifePeriod $id not found")
        }

        // Check if the period is already in use by users
        if (lifePeriodRepository.hasAssessments(id) || lifePeriodRepository.hasSummaries(id)) {
            throw IllegalStateException("Неможливо видалити період, оскільки він уже містить відповіді користувачів. Ви можете лише деактивувати його (isActive = false).")
        }

        val now = Instant.now()
        // Cascading soft delete for questions and their options
        existing.questions.forEach { question ->
            question.deletedAt = now
            question.options.forEach { option ->
                option.deletedAt = now
            }
        }
        
        // Anonymize/prefix code to avoid unique constraint issues
        existing.code = "deleted_${existing.code}_${now.epochSecond}"
        existing.deletedAt = now
        lifePeriodRepository.save(existing)
    }

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

        val prerequisite = request.prerequisiteId?.let {
            questionRepository.findById(it)
                .orElseThrow { IllegalArgumentException("Prerequisite question $it not found") }
        }

        val question = Question(
            text = request.text,
            domain = request.domain,
            weight = request.weight,
            lifePeriod = period,
            prerequisite = prerequisite
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

        val prerequisite = request.prerequisiteId?.let {
            if (it == id) throw IllegalArgumentException("Питання не може бути передумовою самого себе")
            
            val prereq = questionRepository.findById(it)
                .orElseThrow { IllegalArgumentException("Prerequisite question $it not found") }
            
            // Check for cyclic dependency
            checkCyclicDependency(id, it)
            
            prereq
        }

        existing.text = request.text
        existing.domain = request.domain
        existing.weight = request.weight
        existing.prerequisite = prerequisite
        return questionRepository.save(existing)
    }

    private fun checkCyclicDependency(targetId: UUID, prereqId: UUID) {
        var current: Question? = questionRepository.findById(prereqId).orElse(null)
        val visited = mutableSetOf<UUID>()
        visited.add(targetId)
        
        var depth = 0
        val MAX_DEPTH = 20 // Safety limit to prevent infinite loops in DB

        while (current != null && depth < MAX_DEPTH) {
            if (current.id == targetId) {
                throw IllegalArgumentException("Виявлено циклічну залежність: нове правило створює замкнене коло залежностей")
            }
            if (visited.contains(current.id)) break
            visited.add(current.id!!)
            current = current.prerequisite
            depth++
        }
        
        if (depth >= MAX_DEPTH) {
            // Log warning but don't block if we reach extreme depth, 
            // though in developmental milestones 20 levels is more than enough
        }
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun deleteQuestion(id: UUID) {
        val now = Instant.now()
        val existing = questionRepository.findById(id).orElseThrow {
            IllegalArgumentException("Question $id not found")
        }
        
        // Cascading soft delete for options
        existing.options.forEach { it.deletedAt = now }
        
        existing.deletedAt = now
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
    fun deleteAnswerOption(id: UUID) {
        val existing = answerOptionRepository.findById(id).orElseThrow {
            IllegalArgumentException("AnswerOption $id not found")
        }
        existing.deletedAt = Instant.now()
        answerOptionRepository.save(existing)
    }

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
