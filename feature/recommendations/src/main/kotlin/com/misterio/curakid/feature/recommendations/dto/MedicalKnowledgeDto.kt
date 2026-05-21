package com.misterio.curakid.feature.recommendations.dto

data class MedicalKnowledgeDto(
    val id: java.util.UUID,
    val title: String,
    val snippet: String,
    val category: String?
)
