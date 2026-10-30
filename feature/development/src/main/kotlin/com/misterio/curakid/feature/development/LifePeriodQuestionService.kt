package com.misterio.curakid.feature.development

import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import kotlin.collections.emptyList

@Service
class LifePeriodQuestionService(
    private val lifePeriodRepository: LifePeriodRepository,
    private val questionRepository: QuestionRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(LifePeriodQuestionService::class.java)

    companion object {
        fun parseDescription(json: String?, mapper: ObjectMapper = ObjectMapper()): LifePeriodDescription {
            if (json.isNullOrBlank()) return LifePeriodDescription(
                title = "",
                subtitle = "",
                milestones = LifePeriodMilestones(movement = "", speech = "", cognition = ""),
                tips = emptyList()
            )
            return try {
                val root = mapper.readTree(json)
                val ua = root.get("ua") ?: root
                val tipsNode = ua.get("tips") ?: ua.get("tip")
                val parsedTips: List<String> = when {
                    tipsNode?.isArray == true -> {
                        tipsNode.mapNotNull { it.asString()?.takeIf { text -> text.isNotBlank() } }
                    }
                    tipsNode?.isString == true -> {
                        listOfNotNull(tipsNode.asString())
                    }
                    else -> emptyList()
                }
                LifePeriodDescription(
                    title = ua.get("title")?.asString() ?: "",
                    subtitle = ua.get("subtitle")?.asString() ?: if (ua.isString) ua.asString() else "",
                    milestones = LifePeriodMilestones(
                        movement = ua.get("milestones")?.get("movement")?.asString() ?: "",
                        speech = ua.get("milestones")?.get("speech")?.asString() ?: "",
                        cognition = ua.get("milestones")?.get("cognition")?.asString() ?: ""
                    ),
                    tips = parsedTips,
                )
            } catch (e: Exception) {
                LifePeriodDescription("", json, LifePeriodMilestones("", "", ""), emptyList())
            }
        }

        fun parseDescription(json: String?, mapper: Any?): LifePeriodDescription {
            return parseDescription(json)
        }
    }

    @Transactional(readOnly = true)
    @Cacheable("lifePeriods")
    fun getAllPeriodsWithQuestions(): List<LifePeriodResponse> {
        val periods = lifePeriodRepository.findAllWithQuestions()
        val questionIds = periods.flatMap { it.questions }.map { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }
        return periods.map { it.toResponse(objectMapper) }
    }

    @Transactional(readOnly = true)
    @Cacheable("lifePeriods", key = "#code")
    fun getPeriodWithQuestions(code: String): LifePeriodResponse {
        val period = lifePeriodRepository.findByCodeWithQuestions(code)
            .orElseThrow { IllegalArgumentException("Period $code not found") }

        val questionIds = period.questions.map { it.id }
        if (questionIds.isNotEmpty()) {
            questionRepository.findAllWithOptionsByIdIn(questionIds)
        }

        return period.toResponse(objectMapper)
    }

}
