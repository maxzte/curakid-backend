package com.misterio.curakid.admin.vaccines

import com.misterio.curakid.core.database.event.VaccineType
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.springframework.stereotype.Component

@Component
class VaccineCalendarParser {

    data class AgeColumn(
        val index: Int,
        val ageMonths: Int?,
        val ageDays: Int?,
        val isAdult: Boolean = false,
        val isGirlsOnly: Boolean = false,
        val label: String = ""
    )

    fun parse(html: String): List<ParsedVaccine> {
        val doc = Jsoup.parse(html)
        val table = doc.selectFirst("table.c-table__calendar")
            ?: throw IllegalStateException("Could not find table.c-table__calendar on page")

        val rows = table.select("tbody tr")
        if (rows.isEmpty()) {
            throw IllegalStateException("Calendar table has no rows in tbody")
        }

        // Row 0 is the subheader with ages
        val subheaderRow = rows[0]
        val ageCols = parseSubheader(subheaderRow)

        val result = mutableListOf<ParsedVaccine>()

        for (i in 1 until rows.size) {
            val row = rows[i]
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            val rawDiseaseName = cells[0].text().trim()
            if (rawDiseaseName.isBlank()) continue

            val (vaccineName, diseaseName) = normalizeNames(rawDiseaseName)

            // Collect all filled cells for this row to determine total doses
            val rawEntries = mutableListOf<RawDoseEntry>()

            for (col in ageCols) {
                if (col.isAdult) continue
                if (col.index >= cells.size) continue

                val cell = cells[col.index]
                if (!cell.hasClass("c-table__calendar-cell-filled") && !cell.classNames().any { it.contains("filled") }) {
                    continue
                }

                val cellText = cell.text().trim()
                val isRevaccination = cellText.contains("ревакц", ignoreCase = true)
                val type = if (isRevaccination) VaccineType.REVACCINATION else VaccineType.VACCINATION

                val doseNumber = extractDoseNumber(cellText, rawEntries.count { it.type == type } + 1)

                rawEntries.add(
                    RawDoseEntry(
                        doseNumber = doseNumber,
                        type = type,
                        ageMonths = col.ageMonths,
                        ageDays = col.ageDays,
                        notes = if (col.isGirlsOnly) "Рекомендовано для дівчат" else null
                    )
                )
            }

            val countVacc = rawEntries.count { it.type == VaccineType.VACCINATION }
            val countRevacc = rawEntries.count { it.type == VaccineType.REVACCINATION }

            for (entry in rawEntries) {
                val totalDoses = if (entry.type == VaccineType.VACCINATION) {
                    if (countVacc > 0) countVacc else 1
                } else {
                    if (countRevacc > 0) countRevacc else 1
                }

                result.add(
                    ParsedVaccine(
                        name = vaccineName,
                        disease = diseaseName,
                        doseNumber = entry.doseNumber,
                        totalDoses = totalDoses,
                        type = entry.type,
                        scheduledAgeMonths = entry.ageMonths,
                        scheduledAgeDays = entry.ageDays,
                        notes = entry.notes
                    )
                )
            }
        }

        return result
    }

    private data class RawDoseEntry(
        val doseNumber: Int,
        val type: VaccineType,
        val ageMonths: Int?,
        val ageDays: Int?,
        val notes: String?
    )

    private fun parseSubheader(subheaderRow: Element): List<AgeColumn> {
        val cells = subheaderRow.select("td")
        val cols = mutableListOf<AgeColumn>()

        for ((idx, cell) in cells.withIndex()) {
            if (idx == 0) continue // Row label column

            val text = cell.text().trim()
            if (text.isBlank()) continue

            val isAdult = text.contains("Дорослі", ignoreCase = true)
            val isGirls = text.contains("дівчата", ignoreCase = true)

            var months: Int? = null
            var days: Int? = null

            val lower = text.lowercase()
            when {
                lower.contains("доба") || lower.contains("день") || lower.contains("дні") -> {
                    val num = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 2
                    days = num
                    months = 0
                }
                lower.contains("місяц") -> {
                    val num = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 0
                    months = num
                    days = 0
                }
                lower.contains("рок") || lower.contains("рік") -> {
                    val num = Regex("\\d+").find(lower)?.value?.toIntOrNull() ?: 0
                    months = num * 12
                    days = 0
                }
                isAdult -> {
                    // Adult column
                }
            }

            cols.add(
                AgeColumn(
                    index = idx,
                    ageMonths = months,
                    ageDays = days,
                    isAdult = isAdult,
                    isGirlsOnly = isGirls,
                    label = text
                )
            )
        }

        return cols
    }

    private fun extractDoseNumber(cellText: String, fallback: Int): Int {
        val match = Regex("^(\\d+)").find(cellText)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: fallback
    }

    private fun normalizeNames(rawName: String): Pair<String, String> {
        val clean = rawName.trim()
        return when {
            clean.contains("Кір", ignoreCase = true) -> "КПК" to "Кір, паротит, краснуха"
            clean.contains("ВПЛ", ignoreCase = true) -> "ВПЛ" to "Папіломавірус людини (ВПЛ)"
            clean.contains("Хіб", ignoreCase = true) || clean.contains("Hib", ignoreCase = true) -> "Hib" to "Гемофільна інфекція (Hib)"
            clean.contains("Гепатит", ignoreCase = true) -> "Гепатит В" to "Вірусний гепатит В"
            clean.contains("Туберкульоз", ignoreCase = true) -> "БЦЖ" to "Туберкульоз"
            clean.contains("Дифтерія", ignoreCase = true) -> "АДП" to "Дифтерія, правець"
            clean.contains("Кашлюк", ignoreCase = true) -> "АКДП" to "Кашлюк, дифтерія, правець"
            clean.contains("Поліо", ignoreCase = true) -> "ІПВ" to "Поліомієліт"
            else -> clean to clean
        }
    }
}
