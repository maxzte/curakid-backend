package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.essential.common.analysis.*
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Component

@Component
class ReportGenerationEngine(
    private val intelligenceProvider: IntelligenceProvider,
    private val knowledgeProvider: KnowledgeProvider,
    private val chatClient: ChatClient,
    private val promptBuilder: ReportPromptBuilder,
    private val reportEvaluator: ReportEvaluator,
    private val reportContextBuilder: ReportContextBuilder
) {
    private val log = LoggerFactory.getLogger(ReportGenerationEngine::class.java)

    fun generateReport(context: ReportContext): GeneratedReport {
        log.info("Generating report for child={}", context.childId)

        val intelligenceRequest = IntelligenceRequest(
            childId = context.childId.toString(),
            ageMonths = context.ageMonths,
            gender = context.childData.gender.name,
            wfaZscore = context.latestMeasurement?.wfaZscore,
            hfaZscore = context.latestMeasurement?.hfaZscore,
            bmiZscore = context.latestMeasurement?.bmiZscore,
            hcfaZscore = context.latestMeasurement?.hcfaZscore,
            wfhZscore = context.latestMeasurement?.wfhZscore,
            motorGrossScore = context.domainScores?.motorGross,
            motorFineScore = context.domainScores?.motorFine,
            speechScore = context.domainScores?.speech,
            cognitiveScore = context.domainScores?.cognitive,
            socialScore = context.domainScores?.social,
            selfCareScore = context.domainScores?.selfCare,
            sensoryScore = context.domainScores?.sensory,
            rules = context.rules,
            norms = reportContextBuilder.buildFisNorms(context),

            // History & Trends
            wfaVelocity = context.latestMeasurement?.wfaVelocity,
            hfaVelocity = context.latestMeasurement?.hfaVelocity,
            bmiVelocity = context.latestMeasurement?.bmiVelocity,
            hcfaVelocity = context.latestMeasurement?.hcfaVelocity,
            wfhVelocity = context.latestMeasurement?.wfhVelocity,
            regressions = context.domainScores?.regressions ?: emptyMap(),

            // Extended Anamnesis for System 1
            isPremature = context.childData.isPremature,
            allergies = context.childData.allergies,
            chronicConditions = context.childData.chronicConditions,
            pastIllnesses = context.childData.pastIllnesses,
            notes = context.childData.notes,
            daysSinceLastMeasurement = context.latestMeasurement?.daysSinceLast
        )

        val intelligenceResponse = intelligenceProvider.analyze(intelligenceRequest)
        log.debug("intelligenceResponse = {}", intelligenceResponse)
        val urgencyScore = intelligenceResponse.urgencyScore
        val s1Decisions = intelligenceResponse.systemOneDecisions

        val searchQuery = promptBuilder.buildSearchQuery(
            context,
            urgencyScore,
            intelligenceResponse.linguisticInterpretations,
            s1Decisions
        )
        log.debug("searchQuery: {}", searchQuery)

        val anamnesisContext = ChildAnamnesisContext(
            ageMonths = context.ageMonths,
            isPremature = context.childData.isPremature,
            allergies = context.childData.allergies,
            chronicConditions = context.childData.chronicConditions,
            notes = context.childData.notes
        )

        val searchResult = knowledgeProvider.search(
            query = searchQuery,
            urgencyScore = urgencyScore,
            ageMonths = context.ageMonths,
            childAnamnesis = anamnesisContext
        )
        log.debug("searchResult: {}", searchResult)

        val prompt = promptBuilder.buildPrompt(
            childData = context.childData,
            ageMonths = context.ageMonths,
            ageDays = context.ageDays,
            latestMeasurement = context.latestMeasurement,
            domainScores = context.domainScores,
            urgencyScore = urgencyScore,
            chunks = searchResult.chunks,
            confidence = searchResult.confidence,
            norms = reportContextBuilder.buildRawNorms(context),
            systemOneDecisions = s1Decisions
        )
        log.debug("Prompt generated successfully, length: {}", prompt.length)

        var (reportText, recommendations) = try {
            val fullResponse = chatClient.prompt(prompt).call().content() ?: ""
            if (fullResponse.isBlank()) {
                log.warn("Gemini ChatClient returned empty response for childId={}", context.childId)
            }
            promptBuilder.parseGeminiResponse(fullResponse)
        } catch (ex: Throwable) {
            log.error(
                "Gemini call failed for childId={} [type={}]: {}",
                context.childId,
                ex.javaClass.name,
                ex.message,
                ex
            )
            Pair("Звіт тимчасово недоступний.", "Будь ласка, зверніться до лікаря.")
        }

        var draft = GeneratedReport(
            urgencyScore = urgencyScore,
            reportText = reportText.ifEmpty { "Звіт тимчасово недоступний." },
            recommendations = recommendations.ifEmpty { "Будь ласка, зверніться до лікаря." },
            isFallback = intelligenceResponse.isFallback || searchResult.confidence == SearchConfidence.FALLBACK
        )

        // Synchronous Anti-Hallucination & Medical Safety Guardrail using System 1
        var eval = reportEvaluator.verifyReport(context, searchResult.chunks, draft)

        if (!eval.passedGuardrail && !draft.isFallback) {
            log.warn(
                "Report failed System 1 Guardrail (violations={}). Attempting 1 self-correction retry...",
                eval.violationReasons
            )
            val retryPrompt = promptBuilder.buildCorrectionPrompt(
                originalPrompt = prompt,
                draftReport = draft.reportText,
                draftRecommendations = draft.recommendations,
                violationReasons = eval.violationReasons
            )

            try {
                val retryResponse = chatClient.prompt(retryPrompt).call().content() ?: ""
                val (retryReport, retryReco) = promptBuilder.parseGeminiResponse(retryResponse)
                val retryDraft = GeneratedReport(
                    urgencyScore = urgencyScore,
                    reportText = retryReport.ifEmpty { draft.reportText },
                    recommendations = retryReco.ifEmpty { draft.recommendations },
                    isFallback = false
                )

                val retryEval = reportEvaluator.verifyReport(context, searchResult.chunks, retryDraft)
                if (retryEval.passedGuardrail) {
                    log.info("System 1 Guardrail PASSED after self-correction retry.")
                    draft = retryDraft
                    eval = retryEval
                } else {
                    log.warn(
                        "System 1 Guardrail FAILED after retry (violations={}). Engaging Safe WHO Fallback.",
                        retryEval.violationReasons
                    )
                    draft = GeneratedReport(
                        urgencyScore = urgencyScore,
                        reportText = retryDraft.reportText,
                        recommendations = "Зверніться до педіатра для індивідуальної оцінки розвитку та харчування відповідно до стандартів ВООЗ.",
                        isFallback = true,
                        faithfulnessScore = retryEval.faithfulnessScore,
                        relevanceScore = retryEval.relevanceScore
                    )
                    eval = retryEval
                }
            } catch (ex: Throwable) {
                log.error("Self-correction retry failed: {}", ex.message)
            }
        }

        return draft.copy(
            faithfulnessScore = eval.faithfulnessScore,
            relevanceScore = eval.relevanceScore
        )
    }
}
