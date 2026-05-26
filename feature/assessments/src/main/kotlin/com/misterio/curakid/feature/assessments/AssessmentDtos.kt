package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.assessments.SkillAssessment
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// --- Requests ---
data class SaveAnswersRequest(
    val answers: List<AnswerRequest>,
    val assessmentDate: Instant? = null
)

data class AnswerRequest(
    val questionId: UUID,
    val selectedOptionId: UUID,
    val answeredAt: Instant? = null
)

data class CompletePeriodAssessmentRequest(
    @field:NotNull val periodCode: String,
    val allowEarlyTransition: Boolean = false
)

// --- Responses ---
data class AssessmentResponse(
    val assessmentId: UUID,
    val childId: UUID,
    val answers: List<AssessmentAnswerResponse> = emptyList(),
    val assessmentDate: Instant
)

fun SkillAssessment.toAssessmentResponse() = AssessmentResponse(
    assessmentId = id!!,
    childId = child!!.id!!,
    answers = answers.map {
        AssessmentAnswerResponse(
            assessmentAnswerId = it.id!!,
            question = QuestionResponse(
                questionId = it.question?.id!!,
                text = it.question?.text!!,
                domain = it.question!!.domain.name,
                weight = it.question!!.weight,
                options = it.question!!.options.map { answerOption ->
                    OptionResponse(
                        optionId = answerOption.id!!,
                        text = answerOption.text,
                        value = answerOption.value,
                        displayOrder = answerOption.displayOrder
                    )
                },
            ),
            selectedOption = OptionResponse(
                optionId = it.selectedOption?.id!!,
                text = it.selectedOption?.text!!,
                value = it.selectedOption?.value!!,
                displayOrder = it.selectedOption?.displayOrder!!,
            ),
            answeredAt = it.answeredAt,
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
