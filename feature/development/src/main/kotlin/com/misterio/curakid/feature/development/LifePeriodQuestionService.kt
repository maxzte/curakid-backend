package com.misterio.curakid.feature.development

import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID

@Service
class LifePeriodQuestionService(
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(LifePeriodQuestionService::class.java)

    companion object {
        fun parseDescription(objectMapper: ObjectMapper, json: String?): PeriodDescription {
            if (json.isNullOrBlank()) return PeriodDescription(
                "",
                "",
                Milestones("", "", ""),
                emptyList()
            )
            return try {
                val root = objectMapper.readTree(json)
                val ua = root.get("ua") ?: root
                PeriodDescription(
                    title = ua.get("title")?.asString() ?: "",
                    subtitle = ua.get("subtitle")?.asString()
                        ?: if (ua.isString) ua.asString() else "",
                    milestones = Milestones(
                        movement = ua.get("milestones")?.get("movement")?.asString() ?: "",
                        speech = ua.get("milestones")?.get("speech")?.asString() ?: "",
                        cognition = ua.get("milestones")?.get("cognition")?.asString() ?: ""
                    ),
                    tips = ua.get("tips")?.map { it.asText() } ?: emptyList()
                )
            } catch (e: Exception) {
                PeriodDescription("", json, Milestones("", "", ""), emptyList())
            }
        }
    }

    //    @Transactional(readOnly = true)
    @Cacheable("lifePeriods")
    fun getAllPeriodsWithQuestions(): List<PeriodResponse> {
        val periods = lifePeriodRepository.findAllWithQuestions()
        val questionIds = periods.flatMap { it.questions }.mapNotNull { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }
        return periods.map { it.toResponse() }
    }

    fun getPeriodWithQuestions(code: String): PeriodResponse {
        val period = lifePeriodRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Period $code not found") }

        return period.toResponse()
    }

    private fun LifePeriod.toResponse() = PeriodResponse(
        periodId = id!!,
        code = code,
        label = label,
        description = parseDescription(objectMapper, description),
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
                        .map { o ->
                            OptionResponse(
                                optionId = o.id!!,
                                text = o.text,
                                value = o.value,
                                displayOrder = o.displayOrder
                            )
                        }
                )
            }
    )
}

data class PeriodResponse(
    val periodId: UUID,
    val code: String,
    val label: String,
    val description: PeriodDescription,
    val minMonths: Int,
    val maxMonths: Int,
    val questions: List<QuestionResponse>,
)

data class PeriodDescription(
    val title: String,
    val subtitle: String,
    val milestones: Milestones,
    val tips: List<String>
)

data class Milestones(
    val movement: String,
    val speech: String,
    val cognition: String
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
