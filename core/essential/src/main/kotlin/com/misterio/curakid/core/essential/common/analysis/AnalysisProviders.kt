package com.misterio.curakid.core.essential.common.analysis

import java.math.BigDecimal
import java.util.UUID

interface KnowledgeProvider {
    fun search(query: String, urgencyScore: BigDecimal, ageMonths: Int? = null): List<KnowledgeChunkDto>
}

data class KnowledgeChunkDto(
    val title: String,
    val content: String
)

interface IntelligenceProvider {
    fun analyze(request: IntelligenceRequest): IntelligenceResponse
}

data class IntelligenceRequest(
    val childId: String,
    val ageMonths: Int,
    val gender: String,
    val wfaZscore: BigDecimal?,
    val hfaZscore: BigDecimal?,
    val bmiZscore: BigDecimal?,
    val hcfaZscore: BigDecimal?,
    val wfhZscore: BigDecimal?,
    val motorGrossScore: BigDecimal?,
    val motorFineScore: BigDecimal?,
    val speechScore: BigDecimal?,
    val cognitiveScore: BigDecimal?,
    val socialScore: BigDecimal?,
    val selfCareScore: BigDecimal?,
    val sensoryScore: BigDecimal?,
    val rules: List<FuzzyRule>,
    val norms: Map<String, Map<String, Double>> = emptyMap(),

    // Trend analysis (Growth Velocity)
    val wfaVelocity: BigDecimal? = null,
    val hfaVelocity: BigDecimal? = null,
    val bmiVelocity: BigDecimal? = null,
    val hcfaVelocity: BigDecimal? = null,
    val wfhVelocity: BigDecimal? = null,

    // Development regression flags (per domain)
    val regressions: Map<String, Boolean> = emptyMap()
)

data class FuzzyRule(
    val domain: String,
    val conditions: List<FuzzyCondition>,
    val consequent: String
)

data class FuzzyCondition(
    val variable: String,
    val term: String
)

data class IntelligenceResponse(
    val urgencyScore: BigDecimal,
    val breakdown: Map<String, BigDecimal>,
    val linguisticInterpretations: List<String> = emptyList(),
    val isFallback: Boolean
)

data class KnowledgeDocument(
    val content: String,
    val metadata: Map<String, Any> = emptyMap()
)

interface EmbeddingProvider {
    fun generateEmbedding(text: String): List<Float>?
    fun embed(document: KnowledgeDocument): List<Float>?
}

interface ReportProvider {
    fun generateEmergencyReport(childId: UUID, measurementId: UUID): UUID?
    fun generatePeriodReport(childId: UUID, periodId: UUID): UUID?
}