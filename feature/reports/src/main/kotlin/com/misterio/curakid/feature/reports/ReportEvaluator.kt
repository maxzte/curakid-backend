package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeChunkDto
import com.misterio.curakid.core.essential.common.analysis.ReportEvaluationRequestDto
import com.misterio.curakid.core.essential.common.analysis.ReportEvaluationResponseDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.writeTx

@Component
class ReportEvaluator(
    private val intelligenceProvider: IntelligenceProvider,
    private val aiReportRepository: AiReportRepository,
    private val transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(ReportEvaluator::class.java)

    /**
     * Synchronous Medical Safety & Anti-Hallucination Guardrail powered by System 1 (Laya).
     * Compares draft report against actual verified WHO protocols, anthropometry, skills, and anamnesis.
     */
    internal fun verifyReport(
        context: ReportContext,
        chunks: List<KnowledgeChunkDto>,
        draft: GeneratedReport
    ): ReportEvaluationResponseDto {
        val childContextMap = mutableMapOf<String, Any>(
            "ageMonths" to context.ageMonths,
            "gender" to context.childData.gender.name,
            "isPremature" to context.childData.isPremature,
            "allergies" to context.childData.allergies,
            "chronicConditions" to context.childData.chronicConditions,
            "pastIllnesses" to context.childData.pastIllnesses,
            "notes" to context.childData.notes
        )

        context.latestMeasurement?.let { m ->
            m.wfaZscore?.let { childContextMap["wfaZscore"] = it }
            m.hfaZscore?.let { childContextMap["hfaZscore"] = it }
            m.bmiZscore?.let { childContextMap["bmiZscore"] = it }
        }

        val request = ReportEvaluationRequestDto(
            childContext = childContextMap,
            chunks = chunks,
            reportText = draft.reportText,
            recommendations = draft.recommendations
        )

        val evalResponse = intelligenceProvider.evaluateReport(request)
        log.info(
            "Report evaluated via System 1: passed={}, faithfulness={}, relevance={}, violations={}",
            evalResponse.passedGuardrail,
            evalResponse.faithfulnessScore,
            evalResponse.relevanceScore,
            evalResponse.violationReasons
        )
        return evalResponse
    }

    internal fun saveEvaluationScores(
        reportId: java.util.UUID,
        faithfulness: Double,
        relevance: Double
    ) {
        transactionManager.writeTx {
            aiReportRepository.updateEvaluationScores(reportId, faithfulness, relevance)
        }
    }
}
