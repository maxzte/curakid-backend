package com.misterio.curakid.assessments

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// --- Requests ---
data class SaveAssessmentRequest(
    @field:NotNull val domain: AssessmentDomain,
    val rawData: Map<String, Any>?,
    val assessmentDate: LocalDateTime?
)

data class CompletePeriodAssessmentRequest(
    @field:NotNull val periodCode: String,
    val allowEarlyTransition: Boolean = false
)

// --- Responses ---
data class AssessmentResponse(
    val id: UUID,
    val childId: UUID,
    val domain: AssessmentDomain,
    val score: BigDecimal?,
    val rawData: Map<String, Any>?,
    val assessmentDate: LocalDateTime
) {
    companion object {
        fun from(a: SkillAssessment): AssessmentResponse = AssessmentResponse(
            id = a.id!!,
            childId = a.child!!.id!!,
            domain = a.domain,
            score = a.score,
            rawData = a.rawData,
            assessmentDate = a.assessmentDate
        )
    }
}

data class CompletePeriodAssessmentResponse(
    val periodCode: String,
    val completionPercent: Int,
    val requiredPercent: Int,
    val completedAt: LocalDateTime,
    val assessments: List<AssessmentResponse>
)
