package com.misterio.curakid.feature.fis

import com.misterio.curakid.core.essential.common.analysis.FuzzyRule
import java.math.BigDecimal

data class FisRequest(
    val childId: String,
    val ageMonths: Int,
    val gender: String?,
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
    
    // Trend analysis
    val wfaVelocity: BigDecimal? = null,
    val hfaVelocity: BigDecimal? = null,
    val bmiVelocity: BigDecimal? = null,
    val hcfaVelocity: BigDecimal? = null,
    val wfhVelocity: BigDecimal? = null,
    val regressions: Map<String, Boolean> = emptyMap(),

    // Extended Anamnesis
    val isPremature: Boolean = false,
    val allergies: List<String> = emptyList(),
    val chronicConditions: List<String> = emptyList(),
    val pastIllnesses: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val daysSinceLastMeasurement: Long? = null
)

data class SystemOneDecisionsDto(
    val primaryFocus: String = "ROUTINE_CARE",
    val requiresDoctorConsultProbability: Double = 0.0,
    val allergyOrChronicConstraintProbability: Double = 0.0,
    val measurementOutlierProbability: Double = 0.0,
    val anamnesisSeverityScore: Double = 0.0,
    val isSystemOne: Boolean = false
)

data class FisResponse(
    val urgencyScore: BigDecimal,
    val fisUrgencyScore: BigDecimal? = null,
    val isFallback: Boolean = false,
    val breakdown: Map<String, BigDecimal> = emptyMap(),
    val linguisticInterpretations: List<String> = emptyList(),
    val systemOneDecisions: SystemOneDecisionsDto? = null
)

data class RerankRequestPayload(
    val query: String,
    val childContext: Map<String, Any> = emptyMap(),
    val chunks: List<ChunkPayload> = emptyList()
)

data class ChunkPayload(
    val title: String,
    val content: String,
    val relevanceScore: Double? = null,
    val isSafeAndApplicable: Boolean = true
)

data class RerankResponsePayload(
    val chunks: List<ChunkPayload> = emptyList()
)

data class EvaluateReportPayload(
    val childContext: Map<String, Any> = emptyMap(),
    val chunks: List<ChunkPayload> = emptyList(),
    val reportText: String,
    val recommendations: String
)

data class EvaluateReportResponsePayload(
    val faithfulnessScore: Double = 0.85,
    val relevanceScore: Double = 0.85,
    val passedGuardrail: Boolean = true,
    val violationReasons: List<String> = emptyList(),
    val isSystemOne: Boolean = false
)

data class ChatTriagePayload(
    val message: String,
    val childContext: Map<String, Any> = emptyMap()
)

data class ChatTriageResponsePayload(
    val intent: String = "SYMPTOM_OR_ILLNESS",
    val urgencyLevel: String = "ROUTINE",
    val needsRagSearch: Boolean = true,
    val asksForMedicationOrDiagnosis: Boolean = false,
    val statusMessage: String = "Звіряю з медичними протоколами ВООЗ...",
    val suggestedPrompts: List<String> = emptyList(),
    val isSystemOne: Boolean = false
)
