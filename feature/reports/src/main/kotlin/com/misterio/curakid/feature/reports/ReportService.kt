package com.misterio.curakid.feature.reports

import com.fasterxml.jackson.databind.ObjectMapper
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReport
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.CONSTRAINTS_SECTION
import com.misterio.curakid.core.essential.STYLE_SECTION
import com.misterio.curakid.core.essential.TASK_SECTION
import com.misterio.curakid.core.essential.appendAnthropometry
import com.misterio.curakid.core.essential.appendNorms
import com.misterio.curakid.core.essential.appendSection
import com.misterio.curakid.core.essential.appendSkills
import com.misterio.curakid.core.essential.common.analysis.*
import com.misterio.curakid.core.medical.ChartType
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import com.misterio.curakid.core.essential.common.events.AiReportGeneratedEvent
import org.springframework.context.ApplicationEventPublisher
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
    private val lifePeriodRepository: LifePeriodRepository,
    private val intelligenceProvider: IntelligenceProvider,
    private val knowledgeProvider: KnowledgeProvider,
    private val chatClient: ChatClient,
    private val childRepository: ChildRepository,
    private val fuzzyRuleRepository: FuzzyRuleRepository,
    private val percentileCalculator: WhoPercentileCalculator,
    private val transactionTemplate: TransactionTemplate,
    private val measurementCalculator: MeasurementCalculator,
    @org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor") private val taskExecutor: org.springframework.core.task.TaskExecutor,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
) : ReportProvider {
    private val log = LoggerFactory.getLogger(ReportService::class.java)

    fun getReports(childId: UUID, user: User): List<ReportResponse> {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        return getReports(child)
    }

    fun getReports(child: Child): List<ReportResponse> =
        aiReportRepository.findAllByChildOrderByCreatedAtDesc(child).map { ReportResponse.from(it) }

    fun triggerPeriodReportGeneration(childId: UUID, user: User) {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        val latestSummary = periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)
            .orElseThrow { NoSuchElementException("No period summary found for child") }
        taskExecutor.execute {
            generatePeriodReport(child.id!!, latestSummary.id!!)
        }
    }

    fun triggerReportRegeneration(summaryId: UUID, user: User) {
        val summary = periodSummaryRepository.findByIdAndChildUser(summaryId, user.id)
            .orElseThrow { NoSuchElementException("Period summary not found") }
        taskExecutor.execute {
            regenerateReport(user, summaryId)
        }
    }

    fun getReport(reportId: UUID, currentUser: User): ReportResponse {
        val report = aiReportRepository.findByIdAndChildUser(reportId, currentUser)
            .orElseThrow { NoSuchElementException("Report not found") }
        return ReportResponse.from(report)
    }

    fun getReportStatus(reportId: UUID, user: User): ResponseEntity<Boolean> {
        val report = aiReportRepository.findByIdAndChildUser(reportId, user)
            .orElseThrow { NoSuchElementException("Report not found") }
        return ResponseEntity.ok(!report.reportText.isNullOrBlank())
    }

    override fun generateEmergencyReport(childId: UUID, measurementId: UUID): UUID? {
        val context = prepareEmergencyReportContext(childId, measurementId) ?: return null
        val placeholder = createPlaceholderReport(context)
        enqueueReportGeneration(placeholder.id, context)
        return placeholder.id
    }

    override fun generatePeriodReport(childId: UUID, periodId: UUID): UUID? {
        val context = preparePeriodReportContext(childId, periodId) ?: return null
        val placeholder = createPlaceholderReport(context)
        enqueueReportGeneration(placeholder.id, context)
        return placeholder.id
    }

    @Transactional(readOnly = true)
    private fun prepareEmergencyReportContext(childId: UUID, measurementId: UUID): ReportContext? {
        val child = childRepository.findById(childId).getOrNull() ?: return null
        val measurement = measurementRepository.findById(measurementId).getOrNull() ?: return null

        val ageMonths = child.calculateCorrectedAgeMonths()
        val today = LocalDate.now()
        val birthDate = child.birthDate
        val birthDatePlusMonths = birthDate.plusMonths(ageMonths.toLong())
        val ageDays = ChronoUnit.DAYS.between(birthDatePlusMonths, today).toInt()

        val history =
            measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(
                child, measurement.measurementDate
            )

        return ReportContext(
            childId = child.id,
            ageMonths = ageMonths,
            ageDays = ageDays,
            childData = buildChildData(child),
            latestMeasurement = MeasurementSummary(
                wfaZscore = measurement.wfaZscore,
                hfaZscore = measurement.hfaZscore,
                bmiZscore = measurement.bmiZscore,
                hcfaZscore = measurement.hcfaZscore,
                wfhZscore = measurement.wfhZscore,
                wfaVelocity = measurementCalculator.calculateVelocity(
                    history,
                    measurement.wfaZscore,
                    measurement.measurementDate,
                    { it.wfaZscore }
                ),
                hfaVelocity = measurementCalculator.calculateVelocity(
                    history,
                    measurement.hfaZscore,
                    measurement.measurementDate,
                    { it.hfaZscore }
                ),
                bmiVelocity = measurementCalculator.calculateVelocity(
                    history,
                    measurement.bmiZscore,
                    measurement.measurementDate,
                    { it.bmiZscore }
                ),
                hcfaVelocity = measurementCalculator.calculateVelocity(
                    history,
                    measurement.hcfaZscore,
                    measurement.measurementDate,
                    { it.hcfaZscore }
                ),
                wfhVelocity = measurementCalculator.calculateVelocity(
                    history,
                    measurement.wfhZscore,
                    measurement.measurementDate,
                    { it.wfhZscore }
                ),
                daysSinceLast = history.firstOrNull()?.let {
                    ChronoUnit.DAYS.between(
                        it.measurementDate,
                        measurement.measurementDate
                    )
                }
            ),
            domainScores = null,
            rules = loadFuzzyRules(),
            periodSummaryId = null,
            overrideMeasurementId = measurement.id
        )
    }

    @Transactional(readOnly = true)
    internal fun preparePeriodReportContext(childId: UUID, periodId: UUID): ReportContext? {
        val child = childRepository.findById(childId).getOrNull() ?: return null
        val periodSummary = periodSummaryRepository.findById(periodId).getOrNull() ?: return null

        val ageMonths = child.calculateCorrectedAgeMonths()
        val today = LocalDate.now()
        val birthDate = child.birthDate
        val birthDatePlusMonths = birthDate.plusMonths(ageMonths.toLong())
        val ageDays = ChronoUnit.DAYS.between(birthDatePlusMonths, today).toInt()

        val latestMeasurement =
            measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child).orElse(null)
        val history = if (latestMeasurement != null) {
            measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(
                child, latestMeasurement.measurementDate
            )
        } else emptyList()

        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)

        // Regression logic
        val currentLifePeriod = periodSummary.lifePeriod
        val minMonths = currentLifePeriod?.minMonths
        val previousLifePeriod = if (minMonths != null) {
            lifePeriodRepository.findAll().find { it.maxMonths == minMonths }
        } else null

        val regressions = mutableMapOf<String, Boolean>()

        fun checkRegression(domain: AssessmentDomain, currentScore: BigDecimal?): BigDecimal? {
            if (currentScore == null || previousLifePeriod == null) return currentScore
            val prevScore =
                assessments.find { it.domain == domain && it.lifePeriod?.id == previousLifePeriod.id }?.score
            if (prevScore != null && currentScore < prevScore.multiply(BigDecimal("0.9"))) {
                regressions[domain.name] = true
            }
            return currentScore
        }

        val scores = DomainScores(
            motorGross = checkRegression(
                AssessmentDomain.MOTOR_GROSS,
                assessments.firstOrNull { it.domain == AssessmentDomain.MOTOR_GROSS && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            motorFine = checkRegression(
                AssessmentDomain.MOTOR_FINE,
                assessments.firstOrNull { it.domain == AssessmentDomain.MOTOR_FINE && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            speech = checkRegression(
                AssessmentDomain.SPEECH,
                assessments.firstOrNull { it.domain == AssessmentDomain.SPEECH && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            cognitive = checkRegression(
                AssessmentDomain.COGNITIVE,
                assessments.firstOrNull { it.domain == AssessmentDomain.COGNITIVE && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            social = checkRegression(
                AssessmentDomain.SOCIAL_EMOTIONAL,
                assessments.firstOrNull { it.domain == AssessmentDomain.SOCIAL_EMOTIONAL && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            selfCare = checkRegression(
                AssessmentDomain.SELF_CARE,
                assessments.firstOrNull { it.domain == AssessmentDomain.SELF_CARE && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            sensory = checkRegression(
                AssessmentDomain.SENSORY,
                assessments.firstOrNull { it.domain == AssessmentDomain.SENSORY && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            regressions = regressions
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
                    wfaVelocity = measurementCalculator.calculateVelocity(
                        history,
                        it.wfaZscore,
                        it.measurementDate,
                        { m -> m.wfaZscore }),
                    hfaVelocity = measurementCalculator.calculateVelocity(
                        history,
                        it.hfaZscore,
                        it.measurementDate,
                        { m -> m.hfaZscore }),
                    bmiVelocity = measurementCalculator.calculateVelocity(
                        history,
                        it.bmiZscore,
                        it.measurementDate,
                        { m -> m.bmiZscore }),
                    hcfaVelocity = measurementCalculator.calculateVelocity(
                        history,
                        it.hcfaZscore,
                        it.measurementDate,
                        { m -> m.hcfaZscore }),
                    wfhVelocity = measurementCalculator.calculateVelocity(
                        history,
                        it.wfhZscore,
                        it.measurementDate,
                        { m -> m.wfhZscore }),
                    daysSinceLast = history.firstOrNull()?.let { h ->
                        ChronoUnit.DAYS.between(
                            h.measurementDate,
                            it.measurementDate
                        )
                    }
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
            norms = buildFisNorms(context),

            // History & Trends
            wfaVelocity = context.latestMeasurement?.wfaVelocity,
            hfaVelocity = context.latestMeasurement?.hfaVelocity,
            bmiVelocity = context.latestMeasurement?.bmiVelocity,
            hcfaVelocity = context.latestMeasurement?.hcfaVelocity,
            wfhVelocity = context.latestMeasurement?.wfhVelocity,
            regressions = context.domainScores?.regressions ?: emptyMap()
        )

        val intelligenceResponse = intelligenceProvider.analyze(intelligenceRequest)
        log.debug("intelligenceResponse = {}", intelligenceResponse)
        val urgencyScore = intelligenceResponse.urgencyScore

        val searchQuery = buildSearchQuery(
            context,
            urgencyScore,
            intelligenceResponse.linguisticInterpretations
        )
        log.debug("searchQuery: {}", searchQuery)
        val searchResult = knowledgeProvider.search(searchQuery, urgencyScore, context.ageMonths)
        log.debug("searchResult: {}", searchResult)

        val prompt = buildPrompt(
            childData = context.childData,
            ageMonths = context.ageMonths,
            ageDays = context.ageDays,
            latestMeasurement = context.latestMeasurement,
            domainScores = context.domainScores,
            urgencyScore = urgencyScore,
            chunks = searchResult.chunks,
            confidence = searchResult.confidence,
            norms = buildRawNorms(context)
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
            isFallback = intelligenceResponse.isFallback || searchResult.confidence == SearchConfidence.FALLBACK
        )
    }

    private fun saveGeneratedReport(
        context: ReportContext,
        generatedReport: GeneratedReport
    ): AiReport {
        val report = AiReport(
            child = childRepository.getReferenceById(context.childId),
            periodSummary = context.periodSummaryId?.let {
                periodSummaryRepository.getReferenceById(
                    it
                )
            },
            triggerMeasurement = context.overrideMeasurementId?.let {
                measurementRepository.getReferenceById(
                    it
                )
            },
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
            periodSummary = context.periodSummaryId?.let {
                periodSummaryRepository.getReferenceById(
                    it
                )
            },
            triggerMeasurement = context.overrideMeasurementId?.let {
                measurementRepository.getReferenceById(
                    it
                )
            },
            urgencyScore = null,
            reportText = null,
            recommendations = null,
            isFallback = false
        )
        return aiReportRepository.save(report)
    }

    private fun enqueueReportGeneration(reportId: UUID, context: ReportContext) {
        CompletableFuture.runAsync(Runnable {
            try {
                val generated = generateReport(context)
                transactionTemplate.execute {
                    val report = updatePlaceholderReport(reportId, generated)

                    // Trigger Evaluation (asynchronous)
                    if (report != null && !generated.isFallback) {
                        evaluateReportAsync(report, context, generated)

                        // Publish event for push notification
                        val child = report.child
                        val user = child?.user
                        if (child != null && user != null) {
                            eventPublisher.publishEvent(
                                AiReportGeneratedEvent(
                                    userId = user.id,
                                    childId = child.id,
                                    childName = child.name,
                                    reportId = report.id,
                                    isEmergency = context.overrideMeasurementId != null
                                )
                            )
                        }
                    }
                    null
                }
            } catch (ex: Throwable) {
                log.error(
                    "Async report generation failed for reportId={}: {}",
                    reportId,
                    ex.message,
                    ex
                )
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
        }, taskExecutor)
    }

    private fun updatePlaceholderReport(
        reportId: UUID,
        generatedReport: GeneratedReport
    ): AiReport? {
        val report = aiReportRepository.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found") }
        report.apply {
            urgencyScore = generatedReport.urgencyScore
            reportText = generatedReport.reportText
            recommendations = generatedReport.recommendations
            isFallback = generatedReport.isFallback
        }
        return aiReportRepository.save(report)
    }

    private fun evaluateReportAsync(
        report: AiReport,
        context: ReportContext,
        generated: GeneratedReport
    ) {
        CompletableFuture.runAsync(Runnable {
            try {
                val evaluationPrompt = """
                    # ROLE
                    Ти — суворий медичний аудитор. Твоє завдання — оцінити якість згенерованого ШІ звіту для батьків.
                    
                    # INPUT DATA
                    - МЕДИЧНИЙ КОНТЕКСТ (Факти): ${context.childData.notes.joinToString("; ")}
                    - ЗГЕНЕРОВАНИЙ ЗВІТ: ${generated.reportText}
                    - РЕКОМЕНДАЦІЇ: ${generated.recommendations}
                    
                    # METRICS TO EVALUATE (Score 0.0 to 1.0)
                    1. **Faithfulness**: Чи базується звіт ТІЛЬКИ на наданих фактах? (1.0 - повністю відповідає, 0.0 - вигадана інформація/галюцинації).
                    2. **Relevance**: Наскільки звіт корисний та релевантний стану дитини? (1.0 - дуже корисно, 0.0 - загальні фрази без сенсу).
                    
                    # OUTPUT FORMAT (JSON ONLY)
                    {
                      "faithfulness": 0.0,
                      "relevance": 0.0
                    }
                """.trimIndent()

                val response = chatClient.prompt(evaluationPrompt).call().content() ?: ""
                val scores = parseEvaluationResponse(response)

                transactionTemplate.execute {
                    aiReportRepository.updateEvaluationScores(
                        report.id!!,
                        scores.first,
                        scores.second
                    )
                    null
                }
            } catch (ex: Exception) {
                log.error("Report evaluation failed: {}", ex.message)
            }
        }, taskExecutor)
    }

    private fun parseEvaluationResponse(response: String): Pair<Double, Double> {
        return try {
            val json = response.substringAfter("{").substringBeforeLast("}")
            val map = objectMapper.readValue("{$json}", Map::class.java)
            val faith = (map["faithfulness"] as? Number)?.toDouble() ?: 0.5
            val rel = (map["relevance"] as? Number)?.toDouble() ?: 0.5
            Pair(faith, rel)
        } catch (e: Exception) {
            log.warn(
                "Failed to parse LLM evaluation JSON: {}. Response was: {}",
                e.message,
                response
            )
            Pair(0.5, 0.5)
        }
    }

    fun regenerateReport(user: User, summaryId: UUID): ReportResponse {
        val summary = periodSummaryRepository.findById(summaryId)
            .orElseThrow { NoSuchElementException("Period summary not found") }
        if (summary.child?.user?.id != user.id) {
            throw IllegalArgumentException("Access denied")
        }
        val context = preparePeriodReportContext(summary.child!!.id!!, summaryId)
            ?: throw IllegalStateException("Unable to prepare report context")
        val generated = generateReport(context)
        val report = saveGeneratedReport(context, generated)
        return ReportResponse.from(report)
    }

    private fun buildSearchQuery(
        context: ReportContext,
        urgencyScore: BigDecimal,
        linguisticInterpretations: List<String>
    ): String {
        val queryParts = mutableListOf<String>()

        // 1. Core Status (Clinical vs Developmental)
        if (urgencyScore >= BigDecimal("0.7")) {
            queryParts.add("критичні відхилення, патології та медичні протоколи")
        } else {
            queryParts.add("загальний розвиток дитини та стимулюючі вправи")
        }

        // 2. Age context is crucial for embedding accuracy
        queryParts.add("вік ${context.ageMonths} місяців")

        // 3. Trends - Physical Growth
        context.latestMeasurement?.wfaVelocity?.let {
            if (it < BigDecimal("-0.3")) {
                queryParts.add("стійке падіння ваги, зупинка фізичного розвитку, гіпотрофія")
            }
        }
        context.latestMeasurement?.hfaVelocity?.let {
            if (it < BigDecimal("-0.2")) {
                queryParts.add("сповільнення росту, ризики низькорослості")
            }
        }

        // 4. Trends - Skill Regression
        if (context.domainScores?.regressions?.isNotEmpty() == true) {
            queryParts.add("червоні прапорці: регрес розвитку, втрата навичок")
        }

        // 5. FIS Interpretations (Specific symptoms)
        queryParts.addAll(linguisticInterpretations)

        return queryParts.joinToString(", ")
    }

    internal fun buildPrompt(
        childData: ChildData,
        ageMonths: Int,
        ageDays: Int = 0,
        latestMeasurement: MeasurementSummary?,
        domainScores: DomainScores? = null,
        urgencyScore: BigDecimal,
        chunks: List<KnowledgeChunkDto>,
        confidence: SearchConfidence,
        norms: Map<String, Map<String, Double>> = emptyMap()
    ): String = buildString {
        appendLine(
            """
        # ROLE
        Ти — турботливий та висококваліфікований педіатр, який спілкується з батьками.
        Твоя мета: скласти персоналізований, емпатичний, але професійний звіт про розвиток дитини.
        Тон має бути підтримуючим та конструктивним.
        Не став діагнозів. Не вигадуй інформацію.
        """.trimIndent()
        )

        appendSection(
            title = "HARD RULES",
            items = listOf(
                "Звертайся до батьків у привітній та професійній манері.",
                "Пріоритет аналізу: ТРЕНДИ ТА ШВИДКІСТЬ ЗМІН. Якщо за короткий період (менше 7 днів) зафіксовано стрибок дельти > 1.0 — обов'язково вкажи на це. Якщо інтервал дуже малий (0-2 дні), припусти можливість помилки вимірювання.",
                "Якщо антропометрія чи навички в нормі (Z-score від -2 до +2, Skill Score > 60) ТА немає стрімких змін — НЕ згадуй цифри. Напиши, що показники відповідають нормам.",
                "Якщо виявлено відхилення (Z-score < -2 або > +2) АБО стрімку зміну (наприклад, падіння/ріст швидше ніж -0.3 Z/міс) — ОБОВ’ЯЗКОВО зверни на це увагу батьків та порекомендуй консультацію лікаря.",
                "Не пояснюй FIS, Priority Score або внутрішні метрики.",
                "Не повторюй дані з контексту без аналізу.",
                "Кожне речення має містити корисну інформацію або підтримку.",
                "Максимум 5 речень у summary (ЗВІТ).",
                "Уникай сухої констатації фактів, використовуй фрази типу 'Ваш малюк демонструє...', 'Це чудовий показник...', 'Варто звернути увагу на...'"
            )
        )

        appendLine(
            value = """
        # CONTEXT & DATA
        Вік дитини (коригований): $ageMonths міс. та $ageDays дн.
        Стать: ${childData.gender.name}
        Передчасні пологи: ${if (childData.isPremature) "ТАК (вказаний вік є КОРИГОВАНИМ)" else "НІ"}
        """.trimIndent()
        )

        appendSection(
            title = "МЕДИЧНИЙ АНАМНЕЗ",
            items = listOf(
                "Передчасне народження: ${if (childData.isPremature) "ТАК" else "Ні"}",
                "Алергії: ${childData.allergies}",
                "Хронічні захворювання: ${childData.chronicConditions}",
                "Перенесені хвороби: ${childData.pastIllnesses}",
                "Додаткові особливості: ${childData.notes}"
            ),
            footer = "*(Важливо: якщо дитина народилася передчасно, зважай на це при оцінці моторних навичок)*"
        )

        appendAnthropometry(
            wfaZscore = latestMeasurement?.wfaZscore,
            hfaZscore = latestMeasurement?.hfaZscore,
            bmiZscore = latestMeasurement?.bmiZscore,
            hcfaZscore = latestMeasurement?.hcfaZscore,
            wfhZscore = latestMeasurement?.wfhZscore,
        )

        // Trends in prompt
        if (latestMeasurement?.hasVelocity() == true) {
            appendLine("## ТРЕНДИ ТА ДИНАМІКА")
            appendLine("- Інтервал з попереднього заміру: ${latestMeasurement.daysSinceLast ?: "невідомо"} дн.")
            latestMeasurement.wfaVelocity?.let { appendLine("- Швидкість зміни ваги: ${it.toPlainString()} Z-одиниць/міс") }
            latestMeasurement.hfaVelocity?.let { appendLine("- Швидкість росту: ${it.toPlainString()} Z-одиниць/міс") }
            appendLine("*(Увага: стрибки більше 1.0 за короткий період можуть бути помилкою або потребувати уваги)*")
        }

        if (norms.isNotEmpty()) {
            appendNorms(
                norms
            )
        }

        appendSkills(
            motorGross = domainScores?.motorGross,
            motorFine = domainScores?.motorFine,
            speech = domainScores?.speech,
            cognitive = domainScores?.cognitive,
            social = domainScores?.social,
            selfCare = domainScores?.selfCare,
            sensory = domainScores?.sensory,
        )

        // Regression in prompt
        domainScores?.regressions?.filterValues { it }?.keys?.let { regDomains ->
            if (regDomains.isNotEmpty()) {
                appendLine("## ЗАУВАЖЕННЯ ЩОДО РОЗВИТКУ")
                appendLine(
                    "- Помічено регрес (втрату навичок) у доменах: ${
                        regDomains.joinToString(
                            ", "
                        )
                    }"
                )
                appendLine("*(Увага: регрес у навичках потребує детального обговорення з педіатром)*")
            }
        }

        appendLine(
            value = """
        ## INTERNAL PRIORITY SCORE
        Priority: $urgencyScore
        """.trimIndent()
        )

        appendLine(
            value = """
        # KNOWLEDGE BASE
        Використовуй ТІЛЬКИ наведені нижче медичні факти для формування рекомендацій:
        ${chunks.joinToString("\n---\n") { "[${it.title}]\n${it.content}" }}
        """.trimIndent()
        )

        when (confidence) {
            SearchConfidence.CAUTION -> {
                appendLine("\n> [!IMPORTANT]")
                appendLine("> Знайдені протоколи мають часткову схожість. Використовуй їх з обережністю, акцентуй увагу на стандартних медичних нормах та безпеці.")
            }
            SearchConfidence.FALLBACK -> {
                appendLine("\n> [!WARNING]")
                appendLine("> Специфічних медичних протоколів для даного випадку не знайдено. Використовуй ТІЛЬКИ загальні знання ВООЗ та надані загальні рекомендації. Тон має бути максимально обережним. ОБОВ'ЯЗКОВО додай дисклеймер, що звіт базується на загальних нормах через відсутність специфічних даних.")
            }
            else -> {}
        }

        appendLine(value = TASK_SECTION)
        appendLine(value = STYLE_SECTION)
        appendLine(value = CONSTRAINTS_SECTION)
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

        val latest = measurementRepository.findFirstByChildOrderByMeasurementDateDesc(
            childRepository.getReferenceById(context.childId)
        ).orElse(null)
        latest?.heightCm?.let { height ->
            percentileCalculator.getParamsForHeight(gender, height.toDouble(), ageDays)?.let { p ->
                res["WFH (kg)"] = mapOf(
                    "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                )
            }
        }

        return res
    }

    private fun loadFuzzyRules(): List<FuzzyRule> =
        fuzzyRuleRepository.findAllByIsActiveTrue().map {
            FuzzyRule(
                domain = it.domain.name,
                conditions = it.conditions.map { c -> FuzzyCondition(c.variable, c.term) },
                consequent = it.consequent
            )
        }

    private fun parseGeminiResponse(fullText: String): Pair<String, String> {
        // Шукаємо текст після "ЗВІТ", ігноруючи символи #, * та пробіли, зупиняємось перед "РЕКОМЕНДАЦІЇ"
        val reportRegex = Regex(
            """(?i)(?:#|\*)*\s*ЗВІТ:?\s*\*?\s*(.*?)(?=(?:#|\*)*\s*РЕКОМЕНДАЦІЇ:|$)""",
            RegexOption.DOT_MATCHES_ALL
        )

        // Шукаємо текст після "РЕКОМЕНДАЦІЇ" до кінця рядка
        val recoRegex =
            Regex("""(?i)(?:#|\*)*\s*РЕКОМЕНДАЦІЇ:?\s*\*?\s*(.*)""", RegexOption.DOT_MATCHES_ALL)

        val reportMatch = reportRegex.find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: fullText
        val recoMatch = recoRegex.find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: ""

        return Pair(reportMatch, recoMatch)
    }
}

internal data class DomainScores(
    val motorGross: BigDecimal? = null,
    val motorFine: BigDecimal? = null,
    val speech: BigDecimal? = null,
    val cognitive: BigDecimal? = null,
    val social: BigDecimal? = null,
    val selfCare: BigDecimal? = null,
    val sensory: BigDecimal? = null,
    val regressions: Map<String, Boolean> = emptyMap()
)

internal data class ChildData(
    val gender: Gender,
    val isPremature: Boolean,
    val allergies: List<String>,
    val chronicConditions: List<String>,
    val pastIllnesses: List<String>,
    val notes: List<String>,
)

internal data class MeasurementSummary(
    val wfaZscore: BigDecimal?,
    val hfaZscore: BigDecimal?,
    val bmiZscore: BigDecimal?,
    val hcfaZscore: BigDecimal?,
    val wfhZscore: BigDecimal?,
    val wfaVelocity: BigDecimal? = null,
    val hfaVelocity: BigDecimal? = null,
    val bmiVelocity: BigDecimal? = null,
    val hcfaVelocity: BigDecimal? = null,
    val wfhVelocity: BigDecimal? = null,
    val daysSinceLast: Long? = null
) {
    fun hasVelocity() =
        wfaVelocity != null || hfaVelocity != null || bmiVelocity != null || hcfaVelocity != null || wfhVelocity != null
}

private data class GeneratedReport(
    val urgencyScore: BigDecimal,
    val reportText: String,
    val recommendations: String,
    val isFallback: Boolean
)

internal data class ReportContext(
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
