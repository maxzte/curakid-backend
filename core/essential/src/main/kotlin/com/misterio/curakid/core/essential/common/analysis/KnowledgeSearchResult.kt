package com.misterio.curakid.core.essential.common.analysis

data class KnowledgeSearchResult(
    val chunks: List<KnowledgeChunkDto>,
    val confidence: SearchConfidence
)

enum class SearchConfidence {
    /** Similarity > 0.80 (Distance < 0.20) - Full trust */
    HIGH,
    /** Similarity 0.60 - 0.80 (Distance 0.20 - 0.40) - Use with caution */
    CAUTION,
    /** Similarity < 0.60 (Distance > 0.40) or 0 results - Ignore and use general advice */
    FALLBACK
}
