package com.misterio.curakid.reports

import com.misterio.curakid.assessments.AssessmentDomain
import com.misterio.curakid.assessments.SkillAssessmentRepository
import com.misterio.curakid.children.Child
import com.misterio.curakid.fis.FisClient
import com.misterio.curakid.fis.FisRequest
import com.misterio.curakid.knowledge.KnowledgeService
import com.misterio.curakid.measurements.MeasurementRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.util.UUID

@Service
class ReportService(
    private val aiReportRepository: AiReportRepository,
    private val periodSummaryRepository: PeriodSummaryRepository,
    private val measurementRepository: MeasurementRepository,
    private val assessmentRepository: SkillAssessmentRepository,
    private val fisClient: FisClient,
    private val knowledgeService: KnowledgeService,
    private val chatClient: ChatClient
) {
    private val log = LoggerFactory.getLogger(ReportService::class.java)

    fun getReports(child: Child): List<ReportResponse> =
        aiReportRepository.findAllByChildOrderByCreatedAtDesc(child).map { ReportResponse.from(it) }

    fun getReport(reportId: UUID): ReportResponse {
        val report = aiReportRepository.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found") }
        return ReportResponse.from(report)
    }

    /**
     * Main orchestration method:
     * 1. Aggregate child data from DB
     * 2. Call FIS → urgencyScore
     * 3. Search pgvector with KnowledgeService → relevant medical chunks
     * 4. Build prompt → call Gemini via SpringAI
     * 5. Save and return the report
     */
    @Transactional
    fun generateReport(child: Child, periodSummary: PeriodSummary? = null): ReportResponse {
        log.info("Generating report for child={}", child.id)

        val ageMonths = ChronoUnit.MONTHS.between(child.birthDate, LocalDate.now()).toInt()

        // 1. Aggregate latest data
        val measurements = measurementRepository.findAllByChildOrderByMeasurementDateAsc(child)
        val latestMeasurement = measurements.lastOrNull()
        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)

        val motorScore = assessments.firstOrNull { it.domain == AssessmentDomain.MOTOR }?.score
        val speechScore = assessments.firstOrNull { it.domain == AssessmentDomain.SPEECH }?.score
        val cognitiveScore = assessments.firstOrNull { it.domain == AssessmentDomain.COGNITIVE }?.score
        val socialScore = assessments.firstOrNull { it.domain == AssessmentDomain.SOCIAL }?.score

        // 2. Call FIS
        val fisRequest = FisRequest(
            childId = child.id.toString(),
            ageMonths = ageMonths,
            gender = child.gender?.name,
            wfaZscore = latestMeasurement?.wfaZscore,
            hfaZscore = latestMeasurement?.hfaZscore,
            bmiZscore = latestMeasurement?.bmiZscore,
            motorScore = motorScore,
            speechScore = speechScore,
            cognitiveScore = cognitiveScore,
            socialScore = socialScore,
            rules = emptyList() // TODO: load from DB in admin module
        )
        val fisResponse = fisClient.analyze(fisRequest)
        val urgencyScore = fisResponse.urgencyScore

        // 3. Vector search for relevant medical chunks
        val searchQuery = buildSearchQuery(child, ageMonths, urgencyScore, fisResponse.breakdown)
        val knowledgeChunks = knowledgeService.search(searchQuery, urgencyScore)

        // 4. Build prompt and call Gemini
        val prompt = buildPrompt(child, ageMonths, latestMeasurement, motorScore, speechScore,
            cognitiveScore, socialScore, urgencyScore, knowledgeChunks)

        val (reportText, recommendations) = try {
            val fullResponse = chatClient.prompt(prompt).call().content() ?: ""
            parseGeminiResponse(fullResponse)
        } catch (ex: Exception) {
            log.error("Gemini call failed: {}", ex.message)
            Pair("Звіт тимчасово недоступний.", "Будь ласка, зверніться до лікаря.")
        }

        // 5. Save report
        val report = AiReport(
            child = child,
            periodSummary = periodSummary,
            urgencyScore = urgencyScore,
            reportText = reportText,
            recommendations = recommendations,
            isFallback = fisResponse.isFallback
        )
        return ReportResponse.from(aiReportRepository.save(report))
    }

    @Transactional
    fun regenerateReport(summaryId: UUID): ReportResponse {
        val summary = periodSummaryRepository.findById(summaryId)
            .orElseThrow { NoSuchElementException("Period summary not found") }
        return generateReport(summary.child!!, summary)
    }

    // ---- Private helpers ----

    private fun buildSearchQuery(
        child: Child,
        ageMonths: Int,
        urgencyScore: BigDecimal,
        breakdown: Map<String, BigDecimal>
    ): String {
        val urgencyLevel = when {
            urgencyScore >= BigDecimal("0.75") -> "критичний стан"
            urgencyScore >= BigDecimal("0.5") -> "помірна тривожність"
            else -> "норма розвитку"
        }
        val concernedDomains = breakdown.entries
            .filter { it.value >= BigDecimal("0.6") }
            .joinToString(", ") { it.key }
        return "Дитина $ageMonths місяців, ${child.gender?.name ?: "невідома стать"}, " +
                "стан: $urgencyLevel. Проблемні домени: $concernedDomains"
    }

    private fun buildPrompt(
        child: Child,
        ageMonths: Int,
        latestMeasurement: com.misterio.curakid.measurements.Measurement?,
        motorScore: BigDecimal?,
        speechScore: BigDecimal?,
        cognitiveScore: BigDecimal?,
        socialScore: BigDecimal?,
        urgencyScore: BigDecimal,
        chunks: List<com.misterio.curakid.knowledge.KnowledgeChunk>
    ): String {
        val knowledgeContext = chunks.joinToString("\n---\n") { "[${it.title}]\n${it.content}" }
        return """
Ти — лікар-педіатр, що складає персоналізований звіт для батьків.
Відповідай ВИКЛЮЧНО українською мовою. Будь уважним і турботливим.

=== ДАНІ ДИТИНИ ===
Ім'я: ${child.name}
Вік: $ageMonths місяців
Стать: ${child.gender?.name ?: "не вказано"}

=== АНТРОПОМЕТРІЯ (останній замір) ===
Вага (Z-score): ${latestMeasurement?.wfaZscore ?: "немає даних"}
Зріст (Z-score): ${latestMeasurement?.hfaZscore ?: "немає даних"}
BMI (Z-score): ${latestMeasurement?.bmiZscore ?: "немає даних"}

=== ОЦІНКА НАВИЧОК ===
Моторика: ${motorScore ?: "не оцінено"}
Мовлення: ${speechScore ?: "не оцінено"}
Когніція: ${cognitiveScore ?: "не оцінено"}
Соціалізація: ${socialScore ?: "не оцінено"}

=== АНАЛІЗ FIS ===
Рівень тривожності: $urgencyScore (0=норма, 1=критично)

=== МЕДИЧНА БАЗА ЗНАНЬ ===
$knowledgeContext

=== ЗАВДАННЯ ===
Склади звіт із двох частин:
1. ЗВІТ: Аналіз стану дитини (3-5 речень), зрозумілий для батьків
2. РЕКОМЕНДАЦІЇ: Конкретні кроки для батьків (3-5 пунктів)

Використай формат:
ЗВІТ:
<текст звіту>

РЕКОМЕНДАЦІЇ:
<список рекомендацій>
        """.trimIndent()
    }

    private fun parseGeminiResponse(fullText: String): Pair<String, String> {
        val reportMatch = Regex("ЗВІТ:\\s*(.+?)(?=РЕКОМЕНДАЦІЇ:|$)", RegexOption.DOT_MATCHES_ALL)
            .find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: fullText
        val recoMatch = Regex("РЕКОМЕНДАЦІЇ:\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
            .find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: ""
        return Pair(reportMatch, recoMatch)
    }
}
