package com.misterio.curakid.feature.auth.model

import com.misterio.curakid.core.database.auth.ConsentType
import java.time.Instant
import java.util.UUID

data class CreateLegalDocumentRequest(
    val type: String,
    val version: String,
    val url: String,
)

data class LegalDocumentResponse(
    val id: UUID,
    val type: ConsentType,
    val version: String,
    val url: String,
    val isActive: Boolean,
    val publishedAt: Instant
)
