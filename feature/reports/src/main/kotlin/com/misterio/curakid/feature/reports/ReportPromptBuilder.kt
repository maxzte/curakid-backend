package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.essential.CONSTRAINTS_SECTION
import com.misterio.curakid.core.essential.STYLE_SECTION
import com.misterio.curakid.core.essential.TASK_SECTION
import com.misterio.curakid.core.essential.appendAnthropometry
import com.misterio.curakid.core.essential.appendNorms
import com.misterio.curakid.core.essential.appendSection
import com.misterio.curakid.core.essential.appendSkills
import com.misterio.curakid.core.essential.common.analysis.KnowledgeChunkDto
import com.misterio.curakid.core.essential.common.analysis.SearchConfidence
import com.misterio.curakid.core.essential.common.analysis.SystemOneClinicalDecisions
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ReportPromptBuilder {

    internal fun buildSearchQuery(
        context: ReportContext,
        urgencyScore: BigDecimal,
        linguisticInterpretations: List<String>,
        systemOneDecisions: SystemOneClinicalDecisions? = null
    ): String {
        val queryParts = mutableListOf<String>()

        // 1. Core Status (Clinical vs Developmental)
        if (urgencyScore >= BigDecimal("0.7") || (systemOneDecisions?.requiresDoctorConsultProbability ?: 0.0) >= 0.7) {
            queryParts.add("критичні відхилення, патології та медичні протоколи")
        } else {
            queryParts.add("загальний розвиток дитини та стимулюючі вправи")
        }

        // 2. Primary Clinical Focus from System 1 (Laya)
        systemOneDecisions?.primaryFocus?.let { focus ->
            when (focus) {
                "ANTHROPOMETRY_NUTRITION" -> queryParts.add("харчування, набір ваги, фізичний розвиток")
                "MOTOR_DEVELOPMENT" -> queryParts.add("розвиток моторики, рухові вправи")
                "SPEECH_COGNITIVE" -> queryParts.add("розвиток мовлення, комунікація, слух")
                "REGRESSION_ALERT" -> queryParts.add("регрес навичок, втрата функцій, консультація лікаря")
                else -> {}
            }
        }

        // 3. Age context is crucial for embedding accuracy
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
        norms: Map<String, Map<String, Double>> = emptyMap(),
        systemOneDecisions: SystemOneClinicalDecisions? = null
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
            appendNorms(norms)
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

        if (systemOneDecisions != null) {
            appendSection(
                title = "SYSTEM-1 CLINICAL DECISION CONSTRAINTS (MANDATORY)",
                items = buildList {
                    add("Пріоритетний фокус аналізу: ${systemOneDecisions.primaryFocus}")
                    if (systemOneDecisions.requiresDoctorConsultProbability >= 0.60) {
                        add("КЛІНІЧНИЙ ПРІОРИТЕТ: обов'язково і чітко рекомендуй батькам очну консультацію з педіатром.")
                    }
                    if (systemOneDecisions.allergyOrChronicConstraintProbability >= 0.60) {
                        add("ОБМЕЖЕННЯ АНАМНЕЗУ: суворо заборонено радити продукти, масажі чи вправи, що можуть викликати алергічну реакцію або ускладнити хронічні стани.")
                    }
                    if (systemOneDecisions.measurementOutlierProbability >= 0.60) {
                        add("ПОПЕРЕДЖЕННЯ ПРО ПОХИБКУ: зафіксовано різкий стрибок показника за короткий проміжок; припусти можливість похибки вимірювання та порадь переміряти.")
                    }
                }
            )
        }

        appendLine(value = TASK_SECTION)
        appendLine(value = STYLE_SECTION)
        appendLine(value = CONSTRAINTS_SECTION)
    }

    fun parseGeminiResponse(fullText: String): Pair<String, String> {
        val reportRegex = Regex(
            """(?i)(?:#|\*)*\s*ЗВІТ:?\s*\*?\s*(.*?)(?=(?:#|\*)*\s*РЕКОМЕНДАЦІЇ:|$)""",
            RegexOption.DOT_MATCHES_ALL
        )
        val recoRegex =
            Regex("""(?i)(?:#|\*)*\s*РЕКОМЕНДАЦІЇ:?\s*\*?\s*(.*)""", RegexOption.DOT_MATCHES_ALL)

        val reportMatch = reportRegex.find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: fullText
        val recoMatch = recoRegex.find(fullText)?.groupValues?.getOrNull(1)?.trim() ?: ""

        return Pair(reportMatch, recoMatch)
    }

    fun buildCorrectionPrompt(
        originalPrompt: String,
        draftReport: String,
        draftRecommendations: String,
        violationReasons: List<String>
    ): String = buildString {
        appendLine(originalPrompt)
        appendLine()
        appendLine("================================================================================")
        appendLine("УВАГА! ПОПЕРЕДНЯ ЧЕРНЕТКА ЗВІТУ НЕ ПРОЙШЛА АНТИ-ГАЛЮЦИНАЦІЙНИЙ МЕДИЧНИЙ АУДИТ.")
        appendLine("Знайдені критичні порушення:")
        violationReasons.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Попередня відхилена чернетка рекомендацій:")
        appendLine(draftRecommendations)
        appendLine()
        appendLine("ВИМОГА: Перепиши звіт та рекомендації ЗАНОВО. Використовуй ТІЛЬКИ верифіковані факти ВООЗ з наданої бази знань. Не став жодних діагнозів. Усунь усі зазначені порушення!")
        appendLine("================================================================================")
    }
}
