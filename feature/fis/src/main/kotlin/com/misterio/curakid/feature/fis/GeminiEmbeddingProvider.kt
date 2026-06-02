package com.misterio.curakid.feature.fis

import com.misterio.curakid.core.essential.common.analysis.EmbeddingProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeDocument
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class GeminiEmbeddingProvider(
    @Lazy private val embeddingModel: EmbeddingModel
) : EmbeddingProvider {

    override fun generateEmbedding(text: String): List<Float>? {
        return try {
            embeddingModel.embed(text).toList()
        } catch (ex: Exception) {
            null
        }
    }

    override fun embed(document: KnowledgeDocument): List<Float>? {
        return generateEmbedding(document.content)
    }
}
