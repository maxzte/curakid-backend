package com.misterio.curakid.feature.knowledge

import com.misterio.curakid.common.analysis.EmbeddingProvider
import com.misterio.curakid.common.analysis.KnowledgeChunkDto
import com.misterio.curakid.common.analysis.KnowledgeProvider
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class KnowledgeService(
    private val medicalKnowledgeRepository: MedicalKnowledgeRepository,
    private val embeddingProvider: EmbeddingProvider,
) : KnowledgeProvider {

    private val log = LoggerFactory.getLogger(KnowledgeService::class.java)

    companion object {
        /** Кількість чанків для базового пошуку (urgencyScore < 0.5) */
        private const val TOP_K_DEFAULT = 5

        /** Кількість чанків для підвищеного urgency (>= 0.5) */
        private const val TOP_K_URGENT = 8
    }

    /**
     * Шукає найрелевантніші медичні чанки у pgvector за косинусною подібністю.
     *
     * Алгоритм:
     * 1. Генерує ембедінг запиту через FIS-модуль (intfloat/multilingual-e5-large).
     * 2. Форматує вектор у рядок pgvector-літерала `'[f1, f2, …]'`.
     * 3. Викликає нативний SQL-запит через MedicalKnowledgeRepository.
     * 4. При недоступності FIS-модуля повертає порожній список (fallback).
     */
    override fun search(query: String, urgencyScore: BigDecimal): List<KnowledgeChunkDto> {
        log.debug("KnowledgeService.search: query='{}', urgencyScore={}", query, urgencyScore)

        val embedding = embeddingProvider.generateEmbedding(query)
        if (embedding == null) {
            log.warn("EmbeddingProvider returned null — FIS module likely unavailable. Returning empty knowledge context.")
            return emptyList()
        }

        val pgvectorLiteral = embedding.joinToString(separator = ",", prefix = "[", postfix = "]")
        log.debug("pgvector literal length={}", pgvectorLiteral.length)

        val topK = if (urgencyScore >= BigDecimal("0.5")) TOP_K_URGENT else TOP_K_DEFAULT

        val results = medicalKnowledgeRepository.findTopNByEmbeddingSimilarity(
            embedding = pgvectorLiteral,
            limit = topK
        )

        log.debug("Found {} knowledge chunks for urgencyScore={}", results.size, urgencyScore)

        return results.map { knowledge ->
            KnowledgeChunkDto(
                title = knowledge.title,
                content = knowledge.content
            )
        }
    }
}
