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
    private val intelligenceProvider: IntelligenceProvider,
) : KnowledgeProvider {

    private val log = LoggerFactory.getLogger(KnowledgeService::class.java)

    companion object {
        /** Кількість кандидатів для початкового гібридного відбору */
        private const val CANDIDATE_POOL_SIZE = 10

        /** Кількість чанків для базового звіту після реранкінгу */
        private const val TOP_K_DEFAULT = 4

        /** Кількість чанків для підвищеного urgency після реранкінгу */
        private const val TOP_K_URGENT = 6

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
     * Шукає найрелевантніші медичні чанки у pgvector за косинусною подібністю + FTS,
     * після чого виконує верифікацію та реранкінг через System 1 (Laya).
     */
    override fun search(
        query: String,
        urgencyScore: BigDecimal,
        ageMonths: Int?,
        childAnamnesis: ChildAnamnesisContext?
    ): KnowledgeSearchResult {
        log.debug("KnowledgeService.search: query='{}', urgencyScore={}, ageMonths={}", query, urgencyScore, ageMonths)

        val targetCount = if (urgencyScore >= BigDecimal("0.5")) TOP_K_URGENT else TOP_K_DEFAULT

        // 1. Initial Candidate Retrieval: Vector Search + FTS (Pool of up to 10 candidates)
        val vectorResults = performVectorSearch(query, ageMonths, CANDIDATE_POOL_SIZE)
        val ftsResults = performFullTextSearch(query, CANDIDATE_POOL_SIZE)

        // 2. Hybrid Merge (RRF)
        val combinedCandidates = rankReciprocalFusion(vectorResults, ftsResults, CANDIDATE_POOL_SIZE)
        log.debug("Found {} candidate chunks before System 1 reranking", combinedCandidates.size)

        if (combinedCandidates.isEmpty()) {
            return KnowledgeSearchResult(listOf(GENERAL_SAFETY_PROTOCOL), SearchConfidence.FALLBACK)
        }

        // 3. System 1 (Laya) Evidence Gatekeeper & Reranking
        val candidateDtos = combinedCandidates.map { proj ->
            KnowledgeChunkDto(
                title = proj.title,
                content = proj.content
            )
        }

        val childContextMap = mutableMapOf<String, Any>()
        if (childAnamnesis != null) {
            childContextMap["ageMonths"] = childAnamnesis.ageMonths
            childContextMap["isPremature"] = childAnamnesis.isPremature
            childContextMap["allergies"] = childAnamnesis.allergies
            childContextMap["chronicConditions"] = childAnamnesis.chronicConditions
            childContextMap["notes"] = childAnamnesis.notes
        } else if (ageMonths != null) {
            childContextMap["ageMonths"] = ageMonths
        }

        val rerankResponse = intelligenceProvider.rerankChunks(
            RerankRequestDto(
                query = query,
                childContext = childContextMap,
                chunks = candidateDtos
            )
        )

        // Filter out contraindicated or irrelevant chunks and limit to targetCount
        val verifiedChunks = rerankResponse.chunks
            .filter { it.isSafeAndApplicable && (it.relevanceScore ?: 0.5) >= 0.35 }
            .take(targetCount)

        log.debug("Retained {} verified chunks after System 1 filtering", verifiedChunks.size)

        // 4. Calibrated Confidence Level
        // If vector distance indicates caution/fallback, respect it; otherwise consider Laya relevance
        val bestDistance = vectorResults.firstOrNull()?.distance ?: 1.0
        val bestLayaScore = verifiedChunks.firstOrNull()?.relevanceScore ?: 0.0

        val confidence = when {
            verifiedChunks.isEmpty() || bestDistance > DISTANCE_CAUTION -> SearchConfidence.FALLBACK
            bestDistance > DISTANCE_HIGH || bestLayaScore < 0.70 -> SearchConfidence.CAUTION
            else -> SearchConfidence.HIGH
        }

        val finalChunks = if (confidence == SearchConfidence.FALLBACK || verifiedChunks.isEmpty()) {
            listOf(GENERAL_SAFETY_PROTOCOL)
        } else {
            verifiedChunks
        }

        return KnowledgeSearchResult(finalChunks, confidence)
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
            .mapNotNull { idToProjection[it.key] }
    }
}
