package com.misterio.curakid.feature.chat

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ChatRequest(

    @field:NotNull(message = "Chat ID is required")
    val id: UUID,

    @field:NotNull(message = "Child ID is required")
    val childId: UUID?,

    @field:NotBlank(message = "Text cannot be blank")
    @field:Size(max = 2000, message = "Text cannot exceed 2000 characters")
    val text: String?,

)

data class ChatMessageResponse(
    val id: UUID,
    val childId: UUID,
    val userText: String,
    val aiText: String,
    val createdAt: Instant,
    val urgencyLevel: String = "ROUTINE",
    val verifiedSources: List<String> = emptyList()
)

data class ChatStreamMetadata(
    val urgencyLevel: String = "ROUTINE",
    val intent: String = "SYMPTOM_OR_ILLNESS",
    val statusMessage: String = "Звіряю з медичними протоколами ВООЗ...",
    val verifiedSources: List<String> = emptyList(),
    val suggestedPrompts: List<String> = emptyList()
)
