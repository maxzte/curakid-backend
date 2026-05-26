package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReport
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.FuzzyCondition
import com.misterio.curakid.core.essential.common.analysis.FuzzyRule
import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.essential.common.analysis.IntelligenceRequest
import com.misterio.curakid.core.essential.common.analysis.KnowledgeChunkDto
import com.misterio.curakid.core.essential.common.analysis.KnowledgeProvider
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.ChartType
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull

@Service
class ReportService(
    private val aiReportRepository: AiReportRepository,
    private val periodSummaryRepository: PeriodSummaryRepository,
    private val measurementRepository: MeasurementRepository,
    private val assessmentRepository: SkillAssessmentRepository,
    private val intelligenceProvider: IntelligenceProvider,
    private val knowledgeProvider: KnowledgeProvider,
    private val chatClient: ChatClient,
    private val childRepository: ChildRepository,
    private val fuzzyRuleRepository: FuzzyRuleRepository,
    private val percentileCalculator: WhoPercentileCalculator,
    private val transactionTemplate: TransactionTemplate,
) : ReportProvider {
    private val log = LoggerFactory.getLogger(ReportService::class.java)

    fun getReports(child: Child): List<ReportResponse> =
        aiReportRepository.findAllByChildOrderByCreatedAtDesc(child).map { ReportResponse.from(it) }

    fun getReport(reportId: UUID, currentUser: User): ReportResponse {
        val report = aiReportRepository.findByIdAndChildUser(reportId, currentUser)
            .orElseThrow { NoSuchElementException("Report not found") }
        return ReportResponse.from(report)
    }

    fun getReportStatus(reportId: UUID): ResponseEntity<Boolean> {
        val report = aiReportRepository.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found") }
        return ResponseEntity.ok(!report.reportText.isNullOrBlank())
    }

    override fun generateEmergencyReport(childId: UUID, measurementId: UUID): UUID? {
        val context = prepareEmergencyReportContext(childId, measurementId) ?: return null
        val placeholder = createPlaceholderReport(context)
        enqueueReportGeneration(placeholder.id!!, context)
        return placeholder.id
    }

    override fun generatePeriodReport(childId: UUID, periodId: UUID): UUID? {
        val context = preparePeriodReportContext(childId, periodId) ?: return null
        val placeholder = createPlaceholderReport(context)
        enqueueReportGeneration(placeholder.id!!, context)
        return placeholder.id
    }

    @Transactional(readOnly = true)
    private fun prepareEmergencyReportContext(childId: UUID, measurementId: UUID): ReportContext? {
        val child = childRepository.findById(childId).getOrNull() ?: return null
        val measurement = measurementRepository.findById(measurementId).getOrNull() ?: return null

        val today = LocalDate.now()
        val birthDate = child.birthDate
        val ageMonths = ChronoUnit.MONTHS.between(birthDate, today).toInt()
        val birthDatePlusMonths = birthDate.plusMonths(ageMonths.toLong())
        val ageDays = ChronoUnit.DAYS.between(birthDatePlusMonths, today).toInt()

        return ReportContext(
            childId = child.id!!,
            ageMonths = ageMonths,
            ageDays = ageDays,
            childData = buildChildData(child),
            latestMeasurement = MeasurementSummary(
                wfaZscore = measurement.wfaZscore,
                hfaZscore = measurement.hfaZscore,
                bmiZscore = measurement.bmiZscore,
                hcfaZscore = measurement.hcfaZscore,
                wfhZscore = measurement.wfhZscore,
            ),
            domainScores = null,
            rules = loadFuzzyRules(),
            periodSummaryId = null,
            overrideMeasurementId = measurement.id
        )
    }

    @Transactional(readOnly = true)
    private fun preparePeriodReportContext(childId: UUID, periodId: UUID): ReportContext? {
        val child = childRepository.findById(childId).getOrNull() ?: return null
        val periodSummary = periodSummaryRepository.findById(periodId).getOrNull() ?: return null

        val today = LocalDate.now()
        val birthDate = child.birthDate
        val ageMonths = ChronoUnit.MONTHS.between(birthDate, today).toInt()
        val birthDatePlusMonths = birthDate.plusMonths(ageMonths.toLong())
        val ageDays = ChronoUnit.DAYS.between(birthDatePlusMonths, today).toInt()

        val latestMeasurement = measurementRepository.findAllByChildOrderByMeasurementDateAsc(child).lastOrNull()
        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)

        val scores = DomainScores(
            motorGross = assessments.firstOrNull { it.domain == AssessmentDomain.MOTOR_GROSS }?.score,
            motorFine = assessments.firstOrNull { it.domain == AssessmentDomain.MOTOR_FINE }?.score,
            speech = assessments.firstOrNull { it.domain == AssessmentDomain.SPEECH }?.score,
            cognitive = assessments.firstOrNull { it.domain == AssessmentDomain.COGNITIVE }?.score,
            social = assessments.firstOrNull { it.domain == AssessmentDomain.SOCIAL_EMOTIONAL }?.score,
            selfCare = assessments.firstOrNull { it.domain == AssessmentDomain.SELF_CARE }?.score,
            sensory = assessments.firstOrNull { it.domain == AssessmentDomain.SENSORY }?.score,
        )

        return ReportContext(
            childId = child.id!!,
            ageMonths = ageMonths,
            ageDays = ageDays,
            childData = buildChildData(child),
            latestMeasurement = latestMeasurement?.let {
                MeasurementSummary(
                    wfaZscore = it.wfaZscore,
                    hfaZscore = it.hfaZscore,
                    bmiZscore = it.bmiZscore,
                    hcfaZscore = it.hcfaZscore,
                    wfhZscore = it.wfhZscore,
                )
            },
            domainScores = scores,
            rules = loadFuzzyRules(),
            periodSummaryId = periodSummary.id,
            overrideMeasurementId = null
        )
    }

    private fun generateReport(context: ReportContext): GeneratedReport {
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
            norms = buildFisNorms(context)
        )

        val intelligenceResponse = intelligenceProvider.analyze(intelligenceRequest)
        log.debug("intelligenceResponse = {}", intelligenceResponse)
        val urgencyScore = intelligenceResponse.urgencyScore

        val searchQuery = buildSearchQuery(
            context.childData.gender,
            context.ageMonths,
            context.ageDays,
            urgencyScore,
            intelligenceResponse.linguisticInterpretations
        )
        log.debug("searchQuery: {}", searchQuery)
        val knowledgeChunks = knowledgeProvider.search(searchQuery, urgencyScore)
        log.debug("knowledgeChunks: {}", knowledgeChunks)

        val prompt = buildPrompt(
            context.childData,
            context.ageMonths,
            context.ageDays,
            context.latestMeasurement,
            context.domainScores,
            urgencyScore,
            knowledgeChunks,
            buildRawNorms(context)
        )
        log.debug("prompt: {}", prompt)

        var (reportText, recommendations) = try {
            val fullResponse = chatClient.prompt(prompt).call().content() ?: ""
            parseGeminiResponse(fullResponse)
        } catch (ex: Throwable) {
            log.error("Gemini call failed: {}", ex.message, ex)
            Pair("Звіт тимчасово недоступний.", "Будь ласка, зверніться до лікаря.")
        }

        return GeneratedReport(
            urgencyScore = urgencyScore,
            reportText = reportText.ifEmpty { "Звіт тимчасово недоступний." },
            recommendations = recommendations.ifEmpty { "Будь ласка, зверніться до лікаря." },
            isFallback = intelligenceResponse.isFallback
        )
    }

    private fun saveGeneratedReport(context: ReportContext, generatedReport: GeneratedReport): AiReport {
        val report = AiReport(
            child = childRepository.getReferenceById(context.childId),
            periodSummary = context.periodSummaryId?.let { periodSummaryRepository.getReferenceById(it) },
            triggerMeasurement = context.overrideMeasurementId?.let { measurementRepository.getReferenceById(it) },
            urgencyScore = generatedReport.urgencyScore,
            reportText = generatedReport.reportText,
            recommendations = generatedReport.recommendations,
            isFallback = generatedReport.isFallback
        )
        return aiReportRepository.save(report)
    }

    private fun createPlaceholderReport(context: ReportContext): AiReport {
        val report = AiReport(
            child = childRepository.getReferenceById(context.childId),
            periodSummary = context.periodSummaryId?.let { periodSummaryRepository.getReferenceById(it) },
            triggerMeasurement = context.overrideMeasurementId?.let { measurementRepository.getReferenceById(it) },
            urgencyScore = null,
            reportText = null,
            recommendations = null,
            isFallback = false
        )
        return aiReportRepository.save(report)
    }

    private fun enqueueReportGeneration(reportId: UUID, context: ReportContext) {
        CompletableFuture.runAsync {
            try {
                val generated = generateReport(context)
                transactionTemplate.execute {
                    updatePlaceholderReport(reportId, generated)
                    null
                }
            } catch (ex: Throwable) {
                log.error("Async report generation failed for reportId={}: {}", reportId, ex.message, ex)
                transactionTemplate.execute {
                    val fallbackReport = aiReportRepository.findById(reportId)
                        .orElseThrow { NoSuchElementException("Report not found") }
                    fallbackReport.apply {
                        reportText = "Звіт тимчасово недоступний."
                        recommendations = "Будь ласка, зверніться до лікаря."
                        isFallback = true
                    }
                    aiReportRepository.save(fallbackReport)
                    null
                }
            }
        }
    }

    private fun updatePlaceholderReport(reportId: UUID, generatedReport: GeneratedReport) {
        val report = aiReportRepository.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found") }
        report.apply {
            urgencyScore = generatedReport.urgencyScore
            reportText = generatedReport.reportText
            recommendations = generatedReport.recommendations
            isFallback = generatedReport.isFallback
        }
        aiReportRepository.save(report)
    }

    fun regenerateReport(user: User, summaryId: UUID): ReportResponse {
        val summary = periodSummaryRepository.findById(summaryId)
            .orElseThrow { NoSuchElementException("Period summary not found") }
        val context = preparePeriodReportContext(summary.child!!.id!!, summaryId)
            ?: throw IllegalStateException("Unable to prepare report context")
        val generated = generateReport(context)
        val report = saveGeneratedReport(context, generated)
        return ReportResponse.from(report)
    }

    private fun buildSearchQuery(
        gender: Gender,
        ageMonths: Int,
        ageDays: Int,
        urgencyScore: BigDecimal,
        linguisticInterpretations: List<String>
    ): String {
        val status = if (urgencyScore >= BigDecimal("0.7")) {
            "критичні відхилення та патології"
        } else {
            "загальний розвиток та вправи"
        }

        val category = linguisticInterpretations.joinToString(", ")
        val age = "вік $ageMonths міс. та $ageDays дн."
        val childInfo = "${gender.name}, $age"

        return "$status, $category, $childInfo"
    }

    private fun buildPrompt(
        childData: ChildData,
        ageMonths: Int,
        ageDays: Int = 0,
        latestMeasurement: MeasurementSummary?,
        domainScores: DomainScores? = null,
        urgencyScore: BigDecimal,
        chunks: List<KnowledgeChunkDto>,
        norms: Map<String, Map<String, Double>> = emptyMap()
    ): String {
        val knowledgeContext = chunks.joinToString("\n---\n") { "[${it.title}]\n${it.content}" }

        val normsContext = if (norms.isNotEmpty()) {
            val sections = norms.map { (variable, points) ->
                val pointsStr = points.entries.sortedBy { it.key.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
                    .joinToString(", ") { "${it.key}=${it.value}" }
                "- $variable: $pointsStr"
            }.joinToString("\n")
            "## NORMATIVE DATA (WHO Standards for this age/height):\n$sections"
        } else ""

        // Формуємо блок оцінки навичок тільки якщо є дані
        val skillsBlock = domainScores?.let {
            """
        === ОЦІНКА НАВИЧОК (0-100%) ===
        - Груба моторика: ${it.motorGross ?: "не оцінено"}
        - Дрібна моторика: ${it.motorFine ?: "не оцінено"}
        - Мовлення: ${it.speech ?: "не оцінено"}
        - Когніція: ${it.cognitive ?: "не оцінено"}
        - Соціалізація: ${it.social ?: "не оцінено"}
        - Самостійність: ${it.selfCare ?: "не оцінено"}
        - Сенсорна обробка: ${it.sensory ?: "не оцінено"}
        """.trimIndent()
        } ?: "Дані щодо розвитку навичок наразі відсутні."

        val wfa = latestMeasurement?.wfaZscore ?: "немає даних"
        val hfa = latestMeasurement?.hfaZscore ?: "немає даних"
        val bmi = latestMeasurement?.bmiZscore ?: "немає даних"
        val hcfa = latestMeasurement?.hcfaZscore ?: "немає даних"
        val wfh = latestMeasurement?.wfhZscore ?: "немає даних"

        return """
    # ROLE
    Ти — висококваліфікований педіатр та експерт із раннього розвитку дитини. 
    Твоя мета: скласти персоналізований, заспокійливий, але професійний звіт для батьків.
    
    # CONTEXT & DATA
    Вік дитини: $ageMonths міс. та $ageDays дн.
    Стать: ${childData.gender.name}
    
    ## МЕДИЧНИЙ АНАМНЕЗ:
    - Передчасне народження: ${if (childData.isPremature) "ТАК" else "Ні"}
    - Алергії: ${childData.allergies.toBulletPoints()}
    - Хронічні захворювання: ${childData.chronicConditions.toBulletPoints()}
    - Перенесені хвороби: ${childData.pastIllnesses.toBulletPoints()}
    - Додаткові особливості: ${childData.notes.toBulletPoints()}
    *(Важливо: якщо дитина народилася передчасно, зважай на це при оцінці моторних навичок)*
    
    ## АНТРОПОМЕТРІЯ (Z-scores за стандартами ВООЗ):
    Вага (WFA): $wfa
    Зріст (HFA): $hfa
    Індекс маси тіла (BMI): $bmi
    Окружність голови (HCFA): $hcfa
    Вага-до-зросту (WFH): $wfh
    *(Примітка: 0 — ідеал, від -2 до +2 — норма, поза цими межами — потребує уваги)*

    $normsContext

    $skillsBlock

    ## АНАЛІЗ ТРИВОЖНОСТІ (FIS):
    Рівень: $urgencyScore (де 0 — повний спокій, 1 — критична потреба у втручанні).
    *Якщо рівень > 0.7, будь максимально підтримуючим, але серйозним.*

    # KNOWLEDGE BASE
    Використовуй ТІЛЬКИ наведені нижче медичні факти для формування рекомендацій:
    $knowledgeContext

    # TASK
    На основі даних вище, напиши відповідь ВИКЛЮЧНО українською мовою у форматі:

    ### ЗВІТ:
    (Напиши 3-5 речень. Оціни загальний стан: чи відповідає розвиток та антропометрія віковим нормам. Уникай складних медичних термінів, пояснюй їх "людською" мовою.)

    ### РЕКОМЕНДАЦІЇ:
    (Склади 3-5 конкретних, практичних кроків. Кожна порада має бути дієвою: у що пограти, на що звернути увагу, чи варто звернутися до профільного спеціаліста. Базуйся на блоці KNOWLEDGE BASE.)

    # CONSTRAINTS:
    - Не став діагнозів (використовуй фрази "варто звернути увагу", "рекомендовано проконсультуватися").
    - Тон: емпатичний, професійний, без зайвої паніки.
    - Вихідні дані: Тільки Markdown текст.
    """.trimIndent()
    }

    private fun buildChildData(child: Child): ChildData = ChildData(
        gender = child.gender,
        isPremature = child.isPremature,
        allergies = child.allergies.toList(),
        chronicConditions = child.chronicConditions.toList(),
        pastIllnesses = child.pastIllnesses.toList(),
        notes = child.notes.toList()
    )

    private fun buildFisNorms(context: ReportContext): Map<String, Map<String, Double>> {
        val res = mutableMapOf<String, Map<String, Double>>()

        // Z-score standard thresholds (removes magic numbers from Python FIS logic)
        val zNorms = mapOf(
            "P3" to -3.0, "P15" to -2.0, "P50" to 0.0, "P85" to 2.0, "P97" to 3.0
        )

        res["wfaZscore"] = zNorms
        res["hfaZscore"] = zNorms
        res["bmiZscore"] = zNorms
        res["hcfaZscore"] = zNorms
        res["wfhZscore"] = zNorms

        // Skill score standard thresholds (0-100%)
        val skillNorms = mapOf("P3" to 40.0, "P15" to 60.0, "P50" to 85.0)
        res["motorGrossScore"] = skillNorms
        res["motorFineScore"] = skillNorms
        res["speechScore"] = skillNorms
        res["cognitiveScore"] = skillNorms
        res["socialScore"] = skillNorms
        res["selfCareScore"] = skillNorms
        res["sensoryScore"] = skillNorms

        return res
    }

    private fun buildRawNorms(context: ReportContext): Map<String, Map<String, Double>> {
        val gender = context.childData.gender
        val ageDays = (context.ageMonths * 30.44 + context.ageDays).toInt()

        val res = mutableMapOf<String, Map<String, Double>>()

        fun addNorms(variable: String, type: ChartType) {
            percentileCalculator.getParamsForAge(type, gender, ageDays)?.let { p ->
                res[variable] = mapOf(
                    "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                )
            }
        }

        addNorms("WFA (kg)", ChartType.WFA)
        addNorms("HFA (cm)", ChartType.HFA)
        addNorms("BMI", ChartType.BMI)
        addNorms("HCFA (cm)", ChartType.HCFA)

        val latest = measurementRepository.findAllByChildOrderByMeasurementDateAsc(childRepository.getReferenceById(context.childId)).lastOrNull()
        latest?.heightCm?.let { height ->
            percentileCalculator.getParamsForHeight(gender, height.toDouble())?.let { p ->
                res["WFH (kg)"] = mapOf(
                    "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                )
            }
        }

        return res
    }

    private fun loadFuzzyRules(): List<FuzzyRule> = fuzzyRuleRepository.findAllByIsActiveTrue().map {
        FuzzyRule(
            domain = it.domain.name,
            conditions = it.conditions.map { c -> FuzzyCondition(c.variable, c.term) },
            consequent = it.consequent
        )
    }

    private fun List<String>.toBulletPoints() = if (isEmpty()) "відсутні" else this.joinToString(", ")

    private fun parseGeminiResponse(fullText: String): Pair<String, String> {
        // Шукаємо текст після "ЗВІТ", ігноруючи символи #, * та пробіли, зупиняємось перед "РЕКОМЕНДАЦІЇ"
        val reportRegex = Regex(
            """(?i)(?:#|\*)*\s*ЗВІТ:?\s*\*?\s*(.*?)(?=(?:#|\*)*\s*РЕКОМЕНДАЦІЇ:|$)""",
            RegexOption.DOT_MATCHES_ALL
        )

        // Шукаємо текст після "РЕКОМЕНДАЦІЇ" до кінця рядка
        val recoRegex = Regex("""(?i)(?:#|\*)*\s*РЕКОМЕНДАЦІЇ:?\s*\*?\s*(.*)""", RegexOption.DOT_MATCHES_ALL)

        val reportMatch = reportRegex.find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: fullText
        val recoMatch = recoRegex.find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: ""

        return Pair(reportMatch, recoMatch)
    }
}

private data class DomainScores(
    val motorGross: BigDecimal? = null,
    val motorFine: BigDecimal? = null,
    val speech: BigDecimal? = null,
    val cognitive: BigDecimal? = null,
    val social: BigDecimal? = null,
    val selfCare: BigDecimal? = null,
    val sensory: BigDecimal? = null,
)

private data class ChildData(
    val gender: Gender,
    val isPremature: Boolean,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val pastIllnesses: List<String>,
    val notes: List<String>,
)

private data class MeasurementSummary(
    val wfaZscore: BigDecimal?,
    val hfaZscore: BigDecimal?,
    val bmiZscore: BigDecimal?,
    val hcfaZscore: BigDecimal?,
    val wfhZscore: BigDecimal?,
)

private data class GeneratedReport(
    val urgencyScore: BigDecimal,
    val reportText: String,
    val recommendations: String,
    val isFallback: Boolean
)

private data class ReportContext(
    val childId: UUID,
    val ageMonths: Int,
    val ageDays: Int,
    val childData: ChildData,
    val latestMeasurement: MeasurementSummary?,
    val domainScores: DomainScores?,
    val rules: List<FuzzyRule>,
    val periodSummaryId: UUID?,
    val overrideMeasurementId: UUID?
)
