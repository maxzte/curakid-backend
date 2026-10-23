package com.misterio.curakid.core.essential

import java.math.BigDecimal

fun sanitizeUserInput(input: String?): String {
    if (input.isNullOrBlank()) return ""
    // Remove or neutralize prompt injection vectors while preserving human-readable text
    return input
        .replace(Regex("""(?i)\b(ignore previous instructions|disregard instructions|system prompt|jailbreak)\b"""), "[filtered]")
        .replace("```", "'''")
        .replace("<|im_start|>", "")
        .replace("<|im_end|>", "")
        .replace("<system>", "")
        .replace("</system>", "")
        .trim()
}

fun StringBuilder.appendSection(
    title: String,
    items: List<String>,
    footer: String? = null
) {
    appendLine("## $title:")
    items.forEach { appendLine("- $it") }
    footer?.let { appendLine(it) }
    appendLine()
}

fun StringBuilder.appendAnthropometry(
    wfaZscore: BigDecimal?,
    hfaZscore: BigDecimal?,
    bmiZscore: BigDecimal?,
    hcfaZscore: BigDecimal?,
    wfhZscore: BigDecimal?,
) {
    val metrics = listOf(
        "Вага (WFA)" to wfaZscore,
        "Зріст (HFA)" to hfaZscore,
        "Індекс маси тіла (BMI)" to bmiZscore,
        "Окружність голови (HCFA)" to hcfaZscore,
        "Вага-до-зросту (WFH)" to wfhZscore
    )

    appendLine("## АНТРОПОМЕТРІЯ (Z-scores за стандартами ВООЗ):")

    metrics.forEach { (label, value) ->
        appendLine("$label: ${value ?: "немає даних"}")
    }

    appendLine()
}

fun StringBuilder.appendNorms(
    norms: Map<String, Map<String, Double>>
) {
    appendLine("## INTERNAL NORMATIVE DATA (WHO Standards for this age/height):")

    norms.forEach { (variable, points) ->
        val values = points.entries
            .sortedBy { it.key.filter(Char::isDigit).toIntOrNull() ?: 0 }
            .joinToString(", ") { "${it.key}=${it.value}" }

        appendLine("- $variable: $values")
    }

    appendLine()
}

fun StringBuilder.appendSkills(
    motorGross: BigDecimal?,
    motorFine: BigDecimal?,
    speech: BigDecimal?,
    cognitive: BigDecimal?,
    social: BigDecimal?,
    selfCare: BigDecimal?,
    sensory: BigDecimal?,
) {
    if (listOfNotNull(motorGross, motorFine, speech, cognitive, social, selfCare, sensory).isEmpty()) {
        appendLine("Дані щодо розвитку навичок наразі відсутні.")
        appendLine()
        return
    }

    appendLine("=== ОЦІНКА НАВИЧОК (0-100%) ===")

    val skills = listOf(
        "Груба моторика" to motorGross,
        "Дрібна моторика" to motorFine,
        "Мовлення" to speech,
        "Когніція" to cognitive,
        "Соціалізація" to social,
        "Самостійність" to selfCare,
        "Сенсорна обробка" to sensory
    )

    skills.forEach { (name, value) ->
        appendLine("- $name: ${value ?: "не оцінено"}")
    }

    appendLine()
}
