package com.misterio.curakid.admin

import com.misterio.curakid.knowledge.MedicalCategory
import java.math.BigDecimal
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
    val about: String,
    val minMonths: Int,
    val maxMonths: Int
)

data class CreateQuizQuestionRequest(
    val text: String,
    val domain: AssessmentDomain,
    val weight: BigDecimal
)

data class CreateAnswerOptionRequest(
    val text: String,
    val value: String,
    val displayOrder: Int
)

data class UpdateRulesRequest(
    val rules: List<Map<String, Any>>
)

data class UpdateThresholdsRequest(
    val thresholds: Map<String, Any>
)

data class AdminUserResponse(
    val id: UUID,
    val email: String,
    val fullName: String,
    val role: String,
    val isActive: Boolean
)

data class CreateVaccineTemplateRequest(
    val name: String,
    val disease: String,
    val doseNumber: Int,
    val totalDoses: Int,
    val type: String, // String to handle enum from UI
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val notes: String?
)

data class AdminVaccineTemplateResponse(
    val id: UUID,
    val name: String,
    val disease: String,
    val doseNumber: Int,
    val totalDoses: Int,
    val type: String,
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val notes: String?
)
