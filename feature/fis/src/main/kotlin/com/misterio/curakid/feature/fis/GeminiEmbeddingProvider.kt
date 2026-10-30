package com.misterio.curakid.feature.fis

import com.misterio.curakid.core.essential.common.analysis.EmbeddingProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeDocument
import org.slf4j.LoggerFactory
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class GeminiEmbeddingProvider(
    @Lazy private val embeddingModel: EmbeddingModel
) : EmbeddingProvider {
    private val log = LoggerFactory.getLogger(GeminiEmbeddingProvider::class.java)

    @Cacheable(
        value = ["queryEmbeddings"],
        key = "T(org.springframework.util.DigestUtils).md5DigestAsHex(#text.trim().toLowerCase().bytes)",
        unless = "#result == null"
    )
    override fun generateEmbedding(text: String): List<Float>? {
        return try {
            embeddingModel.embed(text).toList()
        } catch (ex: Exception) {
            log.error("Failed to generate embedding with Gemini: {}", ex.message, ex)
            null
        }
    }

    override fun embed(document: KnowledgeDocument): List<Float>? {
        return generateEmbedding(document.content)
    }
}
