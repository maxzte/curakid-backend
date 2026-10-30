package com.misterio.curakid.core.essential.common.analysis

import com.misterio.curakid.core.essential.domain.ChildId
import com.misterio.curakid.core.essential.domain.MeasurementId
import com.misterio.curakid.core.essential.domain.PeriodSummaryId
import com.misterio.curakid.core.essential.domain.ReportId
import com.misterio.curakid.core.essential.domain.toReportId
import java.math.BigDecimal
import java.util.UUID

interface KnowledgeProvider {
    fun search(
        query: String,
        urgencyScore: BigDecimal,
        ageMonths: Int? = null,
        childAnamnesis: ChildAnamnesisContext? = null
    ): KnowledgeSearchResult
}

data class ChildAnamnesisContext(
    val ageMonths: Int,
    val isPremature: Boolean = false,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val notes: List<String> = emptyList()
)

data class KnowledgeChunkDto(
    val title: String,
    val content: String,
    val relevanceScore: Double? = null,
    val isSafeAndApplicable: Boolean = true
)

interface IntelligenceProvider {
    fun analyze(request: IntelligenceRequest): IntelligenceResponse
    fun rerankChunks(request: RerankRequestDto): RerankResponseDto
    fun evaluateReport(request: ReportEvaluationRequestDto): ReportEvaluationResponseDto
    fun triageChat(request: ChatTriageRequestDto): ChatTriageResponseDto
}

data class ChatTriageRequestDto(
    val message: String,
    val childContext: Map<String, Any> = emptyMap()
)

data class ChatTriageResponseDto(
    val intent: String = "SYMPTOM_OR_ILLNESS",
    val urgencyLevel: String = "ROUTINE",
    val needsRagSearch: Boolean = true,
    val asksForMedicationOrDiagnosis: Boolean = false,
    val statusMessage: String = "Звіряю з медичними протоколами ВООЗ...",
    val suggestedPrompts: List<String> = emptyList(),
    val isSystemOne: Boolean = false
)

data class RerankRequestDto(
    val query: String,
    val childContext: Map<String, Any> = emptyMap(),
    val chunks: List<KnowledgeChunkDto> = emptyList()
)

data class RerankResponseDto(
    val chunks: List<KnowledgeChunkDto> = emptyList()
)

data class ReportEvaluationRequestDto(
    val childContext: Map<String, Any> = emptyMap(),
    val chunks: List<KnowledgeChunkDto> = emptyList(),
    val reportText: String,
    val recommendations: String
)

data class ReportEvaluationResponseDto(
    val faithfulnessScore: Double,
    val relevanceScore: Double,
    val passedGuardrail: Boolean,
    val violationReasons: List<String> = emptyList(),
    val isSystemOne: Boolean = false
)

data class SystemOneClinicalDecisions(
    val primaryFocus: String = "ROUTINE_CARE",
    val requiresDoctorConsultProbability: Double = 0.0,
    val allergyOrChronicConstraintProbability: Double = 0.0,
    val measurementOutlierProbability: Double = 0.0,
    val anamnesisSeverityScore: Double = 0.0,
    val isSystemOne: Boolean = false
)

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
    val regressions: Map<String, Boolean> = emptyMap(),

    // Extended Anamnesis for System 1 Decision Model
    val isPremature: Boolean = false,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val pastIllnesses: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val daysSinceLastMeasurement: Long? = null
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
    val fisUrgencyScore: BigDecimal? = null,
    val breakdown: Map<String, BigDecimal>,
    val linguisticInterpretations: List<String> = emptyList(),
    val isFallback: Boolean,
    val systemOneDecisions: SystemOneClinicalDecisions? = null
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

    fun generateEmergencyReport(childId: ChildId, measurementId: MeasurementId): ReportId? =
        generateEmergencyReport(childId.value, measurementId.value)?.toReportId()

    fun generatePeriodReport(childId: ChildId, periodId: PeriodSummaryId): ReportId? =
        generatePeriodReport(childId.value, periodId.value)?.toReportId()
}