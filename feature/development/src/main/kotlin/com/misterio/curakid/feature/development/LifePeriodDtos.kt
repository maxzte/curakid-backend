package com.misterio.curakid.feature.development

import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.feature.development.LifePeriodQuestionService.Companion.parseDescription
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.*

data class LifePeriodResponse(
    val id: UUID,
    val code: String,
    val label: String,
    val description: LifePeriodDescription,
    val minMonths: Int,
    val maxMonths: Int,
    val questions: List<QuestionResponse>,
)

data class LifePeriodDescription(
    val title: String,
    val subtitle: String,
    val milestones: LifePeriodMilestones,
    val tips: List<String>
)

data class LifePeriodMilestones(
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

internal fun LifePeriod.toResponse(objectMapper: ObjectMapper) = LifePeriodResponse(
    id = id,
    code = code,
    label = label,
    description = parseDescription(description, objectMapper),
    minMonths = minMonths,
    maxMonths = maxMonths,
    questions = questions
        .filter { it.isActive }
        .sortedBy { it.weight }
        .map { q ->
            QuestionResponse(
                questionId = q.id,
                text = q.text,
                domain = q.domain.name,
                weight = q.weight,
                options = q.options
                    .sortedBy { it.displayOrder }
                    .map { o ->
                        OptionResponse(
                            optionId = o.id,
                            text = o.text,
                            value = o.value,
                            displayOrder = o.displayOrder
                        )
                    }
            )
        }
)