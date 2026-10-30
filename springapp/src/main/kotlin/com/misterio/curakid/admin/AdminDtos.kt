package com.misterio.curakid.admin

import com.fasterxml.jackson.annotation.JsonProperty
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.feature.development.LifePeriodDescription
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class CreateArticleRequest(
    @field:NotBlank(message = "Title is required")
    val title: String,
    @field:NotBlank(message = "Content is required")
    val content: String,
    @field:NotNull(message = "Category ID is required")
    val categoryId: UUID
)

data class CreateCategoryRequest(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Display name is required")
    val displayName: String
)

data class CreateLifePeriodRequest(
    @field:NotBlank(message = "Code is required")
    val code: String? = null,
    @field:NotBlank(message = "Label is required")
    val label: String? = null,
    val description: String? = null,
    @field:Min(0)
    val minMonths: Int? = null,
    @field:Min(0)
    val maxMonths: Int? = null
)

data class AdminLifePeriodResponse(
    val id: UUID,
    val code: String,
    val label: String,
    val description: LifePeriodDescription,
    val minMonths: Int,
    val maxMonths: Int,
    val questions: List<AdminQuestionResponse> = emptyList()
)

data class AdminQuestionResponse(
    val id: UUID,
    val text: String,
    val domain: AssessmentDomain,
    val weight: BigDecimal,
    val prerequisite: AdminPrerequisiteResponse? = null,
    val options: List<AdminOptionResponse> = emptyList()
)

data class AdminPrerequisiteResponse(
    val id: UUID,
    val text: String
)

data class AdminOptionResponse(
    val id: UUID,
    val text: String,
    val value: Double,
    val order: Int
)

data class CreateQuizQuestionRequest(
    @field:NotBlank
    val text: String,
    @field:NotNull
    val domain: AssessmentDomain,
    @field:NotNull
    val weight: BigDecimal,
    val prerequisiteId: UUID? = null,
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

data class AdminFuzzyConditionResponse(
    val variable: String,
    val term: String
)

data class AdminFuzzyRuleResponse(
    val id: UUID,
    val domain: String,
    val conditions: List<AdminFuzzyConditionResponse>,
    val consequent: String,
    @get:JsonProperty("isActive")
    val isActive: Boolean
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
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Disease is required")
    val disease: String,
    val doseNumber: Int?,
    val totalDoses: Int?,
    @field:NotBlank(message = "Type is required")
    val type: String, // String to handle enum from UI
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val notes: String?
)

data class CreateLegalDocumentRequest(
    @field:NotBlank(message = "Type is required")
    val type: String,
    @field:NotBlank(message = "Version is required")
    val version: String,
    @field:NotBlank(message = "URL is required")
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

data class AdminCategoryResponse(
    val id: UUID,
    val name: String,
    val displayName: String
)

data class AdminArticleResponse(
    val id: UUID,
    val title: String,
    val content: String,
    val category: AdminCategoryResponse?,
    val metadata: Map<String, Any>? = null,
    val createdAt: Instant? = null,
    val sourceId: UUID? = null
)
