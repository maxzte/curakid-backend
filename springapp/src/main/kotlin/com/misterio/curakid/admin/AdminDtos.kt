package com.misterio.curakid.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.feature.development.PeriodDescription
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class CreateArticleRequest(
    val title: String,
    val content: String,
    val categoryId: UUID
)

data class CreateCategoryRequest(
    val name: String,
    val displayName: String
)

data class CreateLifePeriodRequest(
    val code: String,
    val label: String,
    val description: String,
    val minMonths: Int,
    val maxMonths: Int
)

data class AdminLifePeriodResponse(
    val id: UUID,
    val code: String,
    val label: String,
    val description: PeriodDescription,
    val minMonths: Int,
    val maxMonths: Int
)

data class CreateQuizQuestionRequest(
    @field:NotBlank
    val text: String,
    @field:NotNull
    val domain: AssessmentDomain,
    @field:NotNull
    val weight: BigDecimal,
    @field:Valid
    val options: List<CreateAnswerOptionRequest> = emptyList()
)

data class CreateAnswerOptionRequest(
    @field:NotBlank
    val text: String,
    @field:Min(0)
    @field:Max(100)
    val value: Double,
    @field:Min(0)
    val displayOrder: Int
)

data class UpdateRulesRequest(
    val rules: List<Map<String, Any>>
)

data class UpdateThresholdsRequest(
    val thresholds: Map<String, Any>
)

data class AdminPageResponse<T>(
    val items: List<T>,
    val totalItems: Long,
    val totalPages: Int,
    val currentPage: Int
)

data class AdminUserResponse(
    val id: UUID,
    val email: String,
    val fullName: String,
    val role: String,
    @get:JsonProperty("isActive")
    val isActive: Boolean
)

data class CreateVaccineTemplateRequest(
    val name: String,
    val disease: String,
    val doseNumber: Int?,
    val totalDoses: Int?,
    val type: String, // String to handle enum from UI
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val notes: String?
)

data class CreateLegalDocumentRequest(
    val type: String,
    val version: String,
    val url: String,
)

data class AdminLegalDocumentResponse(
    val id: UUID,
    val type: String,
    val version: String,
    val url: String,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val publishedAt: Instant,
)

data class AdminVaccineTemplateResponse(
    val id: UUID,
    val name: String,
    val disease: String,
    val doseNumber: Int?,
    val totalDoses: Int?,
    val type: String,
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val notes: String?
)
