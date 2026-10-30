package com.misterio.curakid.feature.assessments

import com.fasterxml.jackson.annotation.JsonProperty
import com.misterio.curakid.core.database.assessments.SkillAssessment
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// --- Requests ---
data class SaveAnswersRequest(
    @field:NotEmpty(message = "Answers list cannot be empty")
    @field:Size(max = 100, message = "Maximum 100 answers per request")
    @field:Valid
    val answers: List<AnswerRequest>,
    val assessmentDate: Instant? = null
)

data class SaveAnswersResponse(
    val assessments: List<AssessmentResponse>,
    val warnings: List<String> = emptyList()
)

data class AnswerRequest(
    @field:NotNull val questionId: UUID,
    @field:NotNull val selectedOptionId: UUID,
    val answeredAt: Instant? = null
)

data class CompletePeriodAssessmentRequest(
    @field:NotNull val periodCode: String,
    val allowEarlyTransition: Boolean = false
)

data class PeriodReadinessResponse(
    @get:JsonProperty("isReady")
    val isReady: Boolean,
    val completionPercent: Int,
    val requiredPercent: Int,
    val domainStatus: List<DomainReadinessResponse>,
    val reasons: List<String> = emptyList(),
    val canTransitionEarly: Boolean = false
)

data class DomainReadinessResponse(
    val domain: String,
    val completionPercent: Int,
    @get:JsonProperty("isReady")
    val isReady: Boolean,
    val requiredPercent: Int
)

// --- Responses ---
data class AssessmentResponse(
    val assessmentId: UUID,
    val childId: UUID,
    val answers: List<AssessmentAnswerResponse> = emptyList(),
    val assessmentDate: Instant
)

fun SkillAssessment.toAssessmentResponse() = AssessmentResponse(
    assessmentId = id,
    childId = requireNotNull(child?.id) { "Child ID of assessment must not be null" },
    answers = answers.mapNotNull { answer ->
        val q = answer.question ?: return@mapNotNull null
        val opt = answer.selectedOption ?: return@mapNotNull null
        AssessmentAnswerResponse(
            assessmentAnswerId = answer.id,
            question = QuestionResponse(
                questionId = requireNotNull(q.id) { "Question ID must not be null" },
                text = q.text,
                domain = q.domain.name,
                weight = q.weight,
                options = q.options.map { answerOption ->
                    OptionResponse(
                        optionId = answerOption.id,
                        text = answerOption.text,
                        value = answerOption.value,
                        displayOrder = answerOption.displayOrder
                    )
                },
            ),
            selectedOption = OptionResponse(
                optionId = requireNotNull(opt.id) { "Option ID must not be null" },
                text = opt.text,
                value = opt.value,
                displayOrder = opt.displayOrder,
            ),
            answeredAt = answer.answeredAt,
        )
    },
    assessmentDate = assessmentDate
)

data class AssessmentAnswerResponse(
    val assessmentAnswerId: UUID,
    var question: QuestionResponse?,
    var selectedOption: OptionResponse?,
    var answeredAt: Instant,
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

data class CompletePeriodAssessmentResponse(
    val periodCode: String,
    val completionPercent: Int,
    val requiredPercent: Int,
    val completedAt: Instant,
    val assessments: List<AssessmentResponse>,
    val reportId: UUID? = null
)
