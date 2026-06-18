package com.misterio.curakid.feature.knowledge

import com.misterio.curakid.core.essential.common.analysis.*
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository.MedicalKnowledgeProjection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

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

        /** Константа для алгоритму RRF */
        private const val RRF_K = 60

        /** Similarity thresholds mapped to pgvector distances (dist = 1 - sim) */
        private const val DISTANCE_HIGH = 0.20 // Similarity > 0.80
        private const val DISTANCE_CAUTION = 0.40 // Similarity > 0.60

        private val GENERAL_SAFETY_PROTOCOL = KnowledgeChunkDto(
            title = "Загальні рекомендації з безпеки та розвитку (ВООЗ)",
            content = """
                • Завжди консультуйтеся з педіатром при виявленні будь-яких нетипових симптомів.
                • Регулярно проводьте вимірювання зросту та ваги для відстеження динаміки.
                • Забезпечте безпечне середовище для дитини відповідно до її вікових можливостей.
                • Слідкуйте за базовими "червоними прапорцями" розвитку: втрата навичок, відсутність реакції на звуки або зоровий контакт.
                • Дотримуйтеся графіку профілактичних оглядів та вакцинації.
            """.trimIndent()
        )
    }

    /**
     * Шукає найрелевантніші медичні чанки у pgvector за косинусною подібністю + FTS.
     */
    override fun search(query: String, urgencyScore: BigDecimal, ageMonths: Int?): KnowledgeSearchResult {
        log.debug("KnowledgeService.search: query='{}', urgencyScore={}, ageMonths={}", query, urgencyScore, ageMonths)

        val topK = if (urgencyScore >= BigDecimal("0.5")) TOP_K_URGENT else TOP_K_DEFAULT

        // 1. Vector Search
        val vectorResults = performVectorSearch(query, ageMonths, topK)
        
        // 2. Full-Text Search (FTS)
        val ftsResults = performFullTextSearch(query, topK)

        // 3. Hybrid Reranking (Rank Reciprocal Fusion)
        val combinedResults = rankReciprocalFusion(vectorResults, ftsResults, topK)

        log.debug("Found {} knowledge chunks after hybrid reranking", combinedResults.size)

        // 4. Determine Confidence Level
        val bestDistance = vectorResults.firstOrNull()?.distance ?: 1.0
        val confidence = when {
            combinedResults.isEmpty() -> SearchConfidence.FALLBACK
            bestDistance <= DISTANCE_HIGH -> SearchConfidence.HIGH
            bestDistance <= DISTANCE_CAUTION -> SearchConfidence.CAUTION
            else -> SearchConfidence.FALLBACK
        }

        val chunks = if (confidence == SearchConfidence.FALLBACK) {
            listOf(GENERAL_SAFETY_PROTOCOL)
        } else {
            combinedResults.map { proj ->
                KnowledgeChunkDto(
                    title = proj.title,
                    content = proj.content
                )
            }
        }

        return KnowledgeSearchResult(chunks, confidence)
    }

    private fun performVectorSearch(query: String, ageMonths: Int?, limit: Int): List<MedicalKnowledgeProjection> {
        val embedding = embeddingProvider.generateEmbedding(query)
        if (embedding == null) {
            log.warn("EmbeddingProvider returned null — Vector search skipped.")
            return emptyList()
        }

        val pgvectorLiteral = embedding.joinToString(separator = ",", prefix = "[", postfix = "]")
        
        return if (ageMonths != null) {
            medicalKnowledgeRepository.findTopNWithAgeFilter(
                embedding = pgvectorLiteral,
                ageMonths = ageMonths.toDouble(),
                limit = limit
            )
        } else {
            medicalKnowledgeRepository.findTopNByEmbeddingSimilarity(
                embedding = pgvectorLiteral,
                limit = limit
            )
        }
    }

    private fun performFullTextSearch(query: String, limit: Int): List<MedicalKnowledgeProjection> {
        // Sanitize query to avoid SQL injection and syntax errors
        val sanitized = query.replace(Regex("[^a-zA-Z0-9а-яА-Яієґ\\s]"), " ")
        
        // Prepare query for websearch_to_tsquery (simple space-separated words)
        val tsQuery = sanitized.trim()
            .split(Regex("\\s+"))
            .filter { it.length > 1 } // Skip tiny prepositions
            .joinToString(" ")

        return if (tsQuery.isBlank()) emptyList() 
        else medicalKnowledgeRepository.findTopNByFullTextSearch(tsQuery, limit)
    }

    /**
     * Rank Reciprocal Fusion (RRF) algorithm to combine multiple search result lists.
     */
    private fun rankReciprocalFusion(
        vectorList: List<MedicalKnowledgeProjection>,
        ftsList: List<MedicalKnowledgeProjection>,
        limit: Int
    ): List<MedicalKnowledgeProjection> {
        val scores = mutableMapOf<UUID, Double>()
        val idToProjection = mutableMapOf<UUID, MedicalKnowledgeProjection>()

        fun updateScores(list: List<MedicalKnowledgeProjection>) {
            list.forEachIndexed { index, proj ->
                val id = proj.id
                idToProjection[id] = proj
                val rank = index + 1
                val rrfScore = 1.0 / (RRF_K + rank)
                scores[id] = scores.getOrDefault(id, 0.0) + rrfScore
            }
        }

        updateScores(vectorList)
        updateScores(ftsList)

        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { idToProjection[it.key]!! }
    }
}
