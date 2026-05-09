package com.misterio.curakid.measurements

import com.misterio.curakid.children.Gender
import jakarta.annotation.PostConstruct
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Calculates WHO Z-scores for Weight-for-Age, Height-for-Age, and BMI-for-Age.
 *
 * Uses the LMS method: Z = [(X/M)^L - 1] / (S*L)
 * Loads full WHO LMS tables from resources on startup.
 *
 * References: https://www.who.int/tools/child-growth-standards
 */
@Component
class WhoZScoreCalculator {

    data class LmsParams(val l: Double, val m: Double, val s: Double)

    private val wfaBoys = mutableMapOf<Int, LmsParams>()
    private val wfaGirls = mutableMapOf<Int, LmsParams>()
    private val hfaBoys = mutableMapOf<Int, LmsParams>()
    private val hfaGirls = mutableMapOf<Int, LmsParams>()
    private val bmiBoys = mutableMapOf<Int, LmsParams>()
    private val bmiGirls = mutableMapOf<Int, LmsParams>()

    @PostConstruct
    fun init() {
        loadLmsData("zscore/wfa-e-boys.xlsx", wfaBoys)
        loadLmsData("zscore/wfa-e-girls.xlsx", wfaGirls)
        loadLmsData("zscore/lhfa-e-boys.xlsx", hfaBoys)
        loadLmsData("zscore/lhfa-e-girls.xlsx", hfaGirls)
        loadLmsData("zscore/bfa-e-boys.xlsx", bmiBoys)
        loadLmsData("zscore/bfa-e-girls.xlsx", bmiGirls)
    }

    private fun loadLmsData(path: String, map: MutableMap<Int, LmsParams>) {
        ClassPathResource(path).inputStream.use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            for (row in sheet) {
                if (row.rowNum == 0) continue // Skip header
                val dayCell = row.getCell(0) ?: continue

                // POI might treat some cells as text if not careful, but these are numbers
                try {
                    val day = dayCell.numericCellValue.toInt()
                    val l = row.getCell(1)?.numericCellValue ?: 0.0
                    val m = row.getCell(2)?.numericCellValue ?: 0.0
                    val s = row.getCell(3)?.numericCellValue ?: 0.0
                    map[day] = LmsParams(l, m, s)
                } catch (e: Exception) {
                    // Ignore rows that don't parse correctly (e.g., empty rows or text)
                    continue
                }
            }
        }
    }

    /**
     * Weight-for-Age Z-score (for ages 0-1856 days).
     */
    fun calculateWfaZscore(weightKg: BigDecimal, birthDate: LocalDate, gender: Gender?): BigDecimal {
        val ageDays = getAgeInDays(birthDate)
        val params = getParams(ageDays, gender, wfaBoys, wfaGirls)
        return computeZscore(weightKg.toDouble(), params)
    }

    /**
     * Height-for-Age Z-score (for ages 0-1856 days).
     */
    fun calculateHfaZscore(heightCm: BigDecimal, birthDate: LocalDate, gender: Gender?): BigDecimal {
        val ageDays = getAgeInDays(birthDate)
        val params = getParams(ageDays, gender, hfaBoys, hfaGirls)
        return computeZscore(heightCm.toDouble(), params)
    }

    /**
     * BMI-for-Age Z-score: BMI = weight(kg) / height(m)^2
     */
    fun calculateBmiZscore(
        weightKg: BigDecimal,
        heightCm: BigDecimal,
        birthDate: LocalDate,
        gender: Gender?
    ): BigDecimal {
        val heightM = heightCm.toDouble() / 100.0
        val bmi = weightKg.toDouble() / (heightM * heightM)
        val ageDays = getAgeInDays(birthDate)
        val params = getParams(ageDays, gender, bmiBoys, bmiGirls)
        return computeZscore(bmi, params)
    }

    private fun getAgeInDays(birthDate: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(birthDate, LocalDate.now()).toInt()
        return days.coerceIn(0, 1856) // 0 to 60 months approx
    }

    private fun getParams(
        ageDays: Int,
        gender: Gender?,
        boysMap: Map<Int, LmsParams>,
        girlsMap: Map<Int, LmsParams>
    ): LmsParams {
        val isMale = gender != Gender.FEMALE
        val map = if (isMale) boysMap else girlsMap
        // Fallback to 0 days if not found
        return map[ageDays] ?: map.values.lastOrNull() ?: LmsParams(1.0, 1.0, 1.0)
    }

    private fun computeZscore(value: Double, params: LmsParams): BigDecimal {
        val l = params.l
        val m = params.m
        val s = params.s

        val z = if (abs(l) < 0.0001) {
            ln(value / m) / s
        } else {
            ((value / m).pow(l) - 1.0) / (s * l)
        }
        return BigDecimal(z.coerceIn(-5.0, 5.0)).setScale(2, RoundingMode.HALF_UP)
    }
}

