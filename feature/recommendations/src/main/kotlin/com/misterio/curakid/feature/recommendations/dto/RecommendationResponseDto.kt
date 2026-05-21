package com.misterio.curakid.feature.recommendations.dto

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecommendationResponseDto(
    val articles: List<MedicalKnowledgeDto>,
    val exerciseId: Long?
)
