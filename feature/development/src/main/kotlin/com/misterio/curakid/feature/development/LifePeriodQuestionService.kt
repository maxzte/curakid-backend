package com.misterio.curakid.feature.development

import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.*

@Service
class LifePeriodQuestionService(
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository
) {

    @Transactional(readOnly = true)
    @Cacheable("lifePeriods")
    fun getAllPeriodsWithQuestions(): List<PeriodResponse> {
        val periods = lifePeriodRepository.findAllWithQuestions()
        val questionIds = periods.flatMap { it.questions }.mapNotNull { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }
        return periods.map { it.toDTO() }
    }

    fun getPeriodWithQuestions(code: String): PeriodResponse {
        val period = lifePeriodRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Period $code not found") }

        return period.toDTO()
    }

    private fun LifePeriod.toDTO() = PeriodResponse(
        periodId = id!!,
        code = code,
        label = label,
        description = description,
        minMonths = minMonths,
        maxMonths = maxMonths,
        questions = questions
            .sortedBy { it.weight }
            .map { q ->
                QuestionResponse(
                    questionId = q.id!!,
                    text = q.text,
                    domain = q.domain.name,
                    weight = q.weight,
                    options = q.options
                        .sortedBy { it.displayOrder }
                        .map { o -> OptionResponse(optionId = o.id!!, text = o.text, value = o.value, displayOrder = o.displayOrder) }
                )
            }
    )
}

data class PeriodResponse(
    val periodId: UUID,
    val code: String,
    val label: String,
    val description: String,
    val minMonths: Int,
    val maxMonths: Int,
    val questions: List<QuestionResponse>,
)

data class QuestionResponse(
    val questionId: UUID,
    val text: String,
    val domain: String,
    val weight: BigDecimal,
    val options: List<OptionResponse>,
)

data class OptionResponse(
    val optionId: UUID,
    val text: String,
    val value: Double,
    val displayOrder: Int,
)
