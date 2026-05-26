package com.misterio.curakid.feature.chat

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class ChatRequest(
    @field:NotNull(message = "Child ID is required")
    val childId: UUID?,

    @field:NotBlank(message = "Text cannot be blank")
    val text: String?
)

data class ChatMessageResponse(
    val id: UUID,
    val childId: UUID,
    val userText: String,
    val aiText: String,
    val createdAt: Instant
)
