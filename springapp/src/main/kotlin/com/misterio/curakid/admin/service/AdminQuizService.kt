package com.misterio.curakid.admin.service

import com.misterio.curakid.admin.AdminLifePeriodResponse
import com.misterio.curakid.admin.AdminOptionResponse
import com.misterio.curakid.admin.AdminPrerequisiteResponse
import com.misterio.curakid.admin.AdminQuestionResponse
import com.misterio.curakid.admin.CreateAnswerOptionRequest
import com.misterio.curakid.admin.CreateLifePeriodRequest
import com.misterio.curakid.admin.CreateQuizQuestionRequest
import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.essential.common.events.CatalogUpdatedEvent
import com.misterio.curakid.feature.development.LifePeriodQuestionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.annotation.CacheEvict
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class AdminQuizService(
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository,
    private val answerOptionRepository: AnswerOptionRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val objectMapper: ObjectMapper? = null
) {

    fun toResponse(lp: LifePeriod): AdminLifePeriodResponse {
        val mapper = objectMapper ?: ObjectMapper()
        return AdminLifePeriodResponse(
            id = requireNotNull(lp.id) { "LifePeriod ID must not be null" },
            code = lp.code,
            label = lp.label,
            description = LifePeriodQuestionService.parseDescription(lp.description, mapper),
            minMonths = lp.minMonths,
            maxMonths = lp.maxMonths,
            questions = lp.questions.filter { it.isActive }.map { toResponse(it) }
        )
    }

    fun toResponse(q: Question): AdminQuestionResponse {
        return AdminQuestionResponse(
            id = requireNotNull(q.id) { "Question ID must not be null" },
            text = q.text,
            domain = q.domain,
            weight = q.weight,
            prerequisite = q.prerequisite?.let { p ->
                val pid = requireNotNull(p.id) { "Prerequisite ID must not be null" }
                AdminPrerequisiteResponse(pid, p.text)
            },
            options = q.options.filter { it.deletedAt == null }.map { toResponse(it) }
        )
    }

    fun toResponse(opt: AnswerOption): AdminOptionResponse {
        return AdminOptionResponse(
            id = requireNotNull(opt.id) { "Option ID must not be null" },
            text = opt.text,
            value = opt.value,
            order = opt.displayOrder
        )
    }

    // --- Life Periods ---

    @Transactional(readOnly = true)
    fun getLifePeriods(): List<LifePeriod> {
        val periods = lifePeriodRepository.findAllWithQuestions()
        val questionIds = periods.flatMap { it.questions }.map { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }
        return periods
    }

    private fun publishCatalogSync() {
        eventPublisher.publishEvent(CatalogUpdatedEvent("LIFE_PERIODS"))
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun createLifePeriod(request: CreateLifePeriodRequest): AdminLifePeriodResponse {
        val period = LifePeriod(
            code = requireNotNull(request.code) { "Code is required" },
            label = requireNotNull(request.label) { "Label is required" },
            description = request.description ?: "",
            minMonths = request.minMonths ?: 0,
            maxMonths = request.maxMonths ?: 0
        )
        val saved = lifePeriodRepository.save(period)
        publishCatalogSync()
        return toResponse(saved)
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
        publishCatalogSync()
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun updateLifePeriod(id: UUID, request: CreateLifePeriodRequest): AdminLifePeriodResponse {
        val existing = lifePeriodRepository.findById(id).orElseThrow {
            IllegalArgumentException("LifePeriod $id not found")
        }
        request.code?.let { existing.code = it }
        request.label?.let { existing.label = it }
        request.description?.let { existing.description = it }
        request.minMonths?.let { existing.minMonths = it }
        request.maxMonths?.let { existing.maxMonths = it }
        val saved = lifePeriodRepository.save(existing)
        publishCatalogSync()
        return toResponse(saved)
    }

    // --- Questions ---

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun createQuestion(lifePeriodId: UUID, request: CreateQuizQuestionRequest): AdminQuestionResponse {
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
        
        val saved = questionRepository.save(question)
        publishCatalogSync()
        return toResponse(saved)
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun updateQuestion(id: UUID, request: CreateQuizQuestionRequest): AdminQuestionResponse {
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
        val saved = questionRepository.save(existing)
        publishCatalogSync()
        return toResponse(saved)
    }

    private fun checkCyclicDependency(targetId: UUID, prereqId: UUID) {
        var current: Question? = questionRepository.findById(prereqId).orElse(null)
        val visited = mutableSetOf<UUID>()
        visited.add(targetId)
        
        var depth = 0
        val maxDepth = 20 // Safety limit to prevent infinite loops in DB

        while (current != null && depth < maxDepth) {
            val currentId = current.id
            if (currentId == targetId) {
                throw IllegalArgumentException("Виявлено циклічну залежність: нове правило створює замкнене коло залежностей")
            }
            if (visited.contains(currentId)) break
            visited.add(currentId)
            current = current.prerequisite
            depth++
        }
        
        if (depth >= maxDepth) {
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
        publishCatalogSync()
    }

    // --- Answer Options ---

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun createAnswerOption(questionId: UUID, request: CreateAnswerOptionRequest): AdminOptionResponse {
        val question = questionRepository.findById(questionId)
            .orElseThrow { IllegalArgumentException("Question $questionId not found") }

        val option = AnswerOption(
            question = question,
            text = request.text,
            value = request.value,
            displayOrder = request.displayOrder
        )
        val saved = answerOptionRepository.save(option)
        publishCatalogSync()
        return toResponse(saved)
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun deleteAnswerOption(id: UUID) {
        val existing = answerOptionRepository.findById(id).orElseThrow {
            IllegalArgumentException("AnswerOption $id not found")
        }
        existing.deletedAt = Instant.now()
        answerOptionRepository.save(existing)
        publishCatalogSync()
    }

    @Transactional
    @CacheEvict(value = ["lifePeriods"], allEntries = true)
    fun updateAnswerOption(id: UUID, request: CreateAnswerOptionRequest): AdminOptionResponse {
        val existing = answerOptionRepository.findById(id).orElseThrow {
            IllegalArgumentException("AnswerOption $id not found")
        }
        existing.text = request.text
        existing.value = request.value
        existing.displayOrder = request.displayOrder
        val saved = answerOptionRepository.save(existing)
        publishCatalogSync()
        return toResponse(saved)
    }
}
