package com.misterio.curakid.feature.development

import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class LifePeriodQuestionService(
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(LifePeriodQuestionService::class.java)

    companion object {
        fun parseDescription(objectMapper: ObjectMapper, json: String?): LifePeriodDescription {
            if (json.isNullOrBlank()) return LifePeriodDescription(
                "",
                "",
                LifePeriodMilestones("", "", ""),
                emptyList()
            )
            return try {
                val root = objectMapper.readTree(json)
                val ua = root.get("ua") ?: root
                LifePeriodDescription(
                    title = ua.get("title")?.asString() ?: "",
                    subtitle = ua.get("subtitle")?.asString()
                        ?: if (ua.isString) ua.asString() else "",
                    milestones = LifePeriodMilestones(
                        movement = ua.get("milestones")?.get("movement")?.asString() ?: "",
                        speech = ua.get("milestones")?.get("speech")?.asString() ?: "",
                        cognition = ua.get("milestones")?.get("cognition")?.asString() ?: ""
                    ),
                    tips = ua.get("tips")?.map { it.asText() } ?: emptyList()
                )
            } catch (e: Exception) {
                LifePeriodDescription("", json, LifePeriodMilestones("", "", ""), emptyList())
            }
        }
    }

    //    @Transactional(readOnly = true)
    @Cacheable("lifePeriods")
    fun getAllPeriodsWithQuestions(): List<LifePeriodResponse> {
        val periods = lifePeriodRepository.findAllWithQuestions()
        val questionIds = periods.flatMap { it.questions }.mapNotNull { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }
        return periods.map { it.toResponse(objectMapper) }
    }

    fun getPeriodWithQuestions(code: String): LifePeriodResponse {
        val period = lifePeriodRepository.findByCode(code)
            .orElseThrow { IllegalArgumentException("Period $code not found") }

        return period.toResponse(objectMapper)
    }

}
