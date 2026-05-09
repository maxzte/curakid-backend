package com.misterio.curakid.development

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
    fun getAllPeriodsWithQuestions(): List<PeriodDTO> {
        val periods = lifePeriodRepository.findAllWithQuestions()
        val questionIds = periods.flatMap { it.questions }.mapNotNull { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }
        return periods.map { it.toDTO() }
    }

    fun getPeriodWithQuestions(code: String): PeriodDTO {
        val period = lifePeriodRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Period $code not found") }

        return period.toDTO()
    }

    private fun LifePeriod.toDTO() = PeriodDTO(
        id = this.id!!,
        code = this.code,
        label = this.label,
        about = about,
        minMonths = this.minMonths,
        maxMonths = this.maxMonths,
        questions = this.questions
            .sortedBy { it.weight }
            .map { q ->
                QuestionDTO(
                    id = q.id!!,
                    text = q.text,
                    domain = q.domain.name,
                    weight = q.weight,
                    options = q.options
                        .sortedBy { it.order }
                        .map { o -> OptionDTO(id = o.id!!, text = o.text, value = o.value) }
                )
            }
    )
}

data class PeriodDTO(
    val id: UUID,
    val code: String,
    val label: String,
    val about: String,
    val minMonths: Int,
    val maxMonths: Int,
    val questions: List<QuestionDTO>
)

data class QuestionDTO(
    val id: UUID,
    val text: String,
    val domain: String,
    val weight: BigDecimal,
    val options: List<OptionDTO>
)

data class OptionDTO(
    val id: UUID,
    val text: String,
    val value: String
)
