package com.misterio.curakid.core.database.knowledge

import java.util.UUID


interface MedicalKnowledgeWithScore {
    val id: UUID
    val title: String
    val content: String
    val similarityScore: Double
}
