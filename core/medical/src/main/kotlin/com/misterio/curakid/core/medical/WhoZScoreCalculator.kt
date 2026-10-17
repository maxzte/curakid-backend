package com.misterio.curakid.core.medical

import com.misterio.curakid.core.database.children.Gender
import jakarta.annotation.PostConstruct
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

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
    private val wflBoys = mutableMapOf<Int, LmsParams>()
    private val wflGirls = mutableMapOf<Int, LmsParams>()
    private val wfhBoys = mutableMapOf<Int, LmsParams>()
    private val wfhGirls = mutableMapOf<Int, LmsParams>()
    private val bmiBoys = mutableMapOf<Int, LmsParams>()
    private val bmiGirls = mutableMapOf<Int, LmsParams>()
    private val hcfaBoys = mutableMapOf<Int, LmsParams>()
    private val hcfaGirls = mutableMapOf<Int, LmsParams>()

    @PostConstruct
    fun init() {
        loadLmsData("zscore/wfa-e-boys.xlsx", wfaBoys)
        loadLmsData("zscore/wfa-e-girls.xlsx", wfaGirls)
        loadLmsData("zscore/lhfa-e-boys.xlsx", hfaBoys)
        loadLmsData("zscore/lhfa-e-girls.xlsx", hfaGirls)
        loadWfhLmsData("zscore/wfl-e-boys.xlsx", wflBoys)
        loadWfhLmsData("zscore/wfl-e-girls.xlsx", wflGirls)
        loadWfhLmsData("zscore/wfh-e-boys.xlsx", wfhBoys)
        loadWfhLmsData("zscore/wfh-e-girls.xlsx", wfhGirls)
        loadLmsData("zscore/bfa-e-boys.xlsx", bmiBoys)
        loadLmsData("zscore/bfa-e-girls.xlsx", bmiGirls)
        loadLmsData("zscore/hcfa-e-boys.xlsx", hcfaBoys)
        loadLmsData("zscore/hcfa-e-girls.xlsx", hcfaGirls)
    }

    private fun extractLmsParams(row: org.apache.poi.ss.usermodel.Row): LmsParams {
        val l = row.getCell(1)?.numericCellValue ?: 0.0
        val m = row.getCell(2)?.numericCellValue ?: 0.0
        val s = row.getCell(3)?.numericCellValue ?: 0.0
        return LmsParams(l, m, s)
    }

    private fun loadWfhLmsData(path: String, map: MutableMap<Int, LmsParams>) {
        ClassPathResource(path).inputStream.use { inputStream ->
            WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                for (row in sheet) {
                    if (row.rowNum == 0) continue // Skip header
                    val heightCell = row.getCell(0) ?: continue

                    try {
                        val heightKey = (heightCell.numericCellValue * 10).roundToInt()
                        map[heightKey] = extractLmsParams(row)
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }
    }

    private fun loadLmsData(path: String, map: MutableMap<Int, LmsParams>) {
        ClassPathResource(path).inputStream.use { inputStream ->
            WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                for (row in sheet) {
                    if (row.rowNum == 0) continue // Skip header
                    val dayCell = row.getCell(0) ?: continue

                    try {
                        val day = dayCell.numericCellValue.toInt()
                        map[day] = extractLmsParams(row)
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }
    }

    /**
     * Weight-for-Age Z-score (for ages 0-1856 days).
     */
    fun calculateWfaZscore(
        weightKg: BigDecimal,
        birthDate: LocalDate,
        gender: Gender?,
        measurementDate: LocalDate = LocalDate.now()
    ): BigDecimal {
        val ageDays = getAgeInDays(birthDate, measurementDate)
        val params = getParams(ageDays, gender, wfaBoys, wfaGirls)
        return computeZscore(weightKg.toDouble(), params)
    }

    /**
     * Height-for-Age Z-score (for ages 0-1856 days).
     */
    fun calculateHfaZscore(
        heightCm: BigDecimal,
        birthDate: LocalDate,
        gender: Gender?,
        measurementDate: LocalDate = LocalDate.now()
    ): BigDecimal {
        val ageDays = getAgeInDays(birthDate, measurementDate)
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
        gender: Gender?,
        measurementDate: LocalDate = LocalDate.now()
    ): BigDecimal {
        val heightM = heightCm.toDouble() / 100.0
        val bmi = weightKg.toDouble() / (heightM * heightM)
        val ageDays = getAgeInDays(birthDate, measurementDate)
        val params = getParams(ageDays, gender, bmiBoys, bmiGirls)
        return computeZscore(bmi, params)
    }

    /**
     * Head Circumference-for-Age Z-score (for ages 0-1856 days).
     */
    fun calculateHcfaZscore(
        headCircumferenceCm: BigDecimal,
        birthDate: LocalDate,
        gender: Gender?,
        measurementDate: LocalDate = LocalDate.now()
    ): BigDecimal {
        val ageDays = getAgeInDays(birthDate, measurementDate)
        val params = getParams(ageDays, gender, hcfaBoys, hcfaGirls)
        return computeZscore(headCircumferenceCm.toDouble(), params)
    }

    /**
     * Weight-for-Height Z-score (based on height/length in cm).
     * Uses WFL (Weight-for-Length) for children < 2 years (730 days).
     * Uses WFH (Weight-for-Height) for children >= 2 years.
     */
    fun calculateWfhZscore(
        weightKg: BigDecimal,
        heightCm: BigDecimal,
        birthDate: LocalDate,
        gender: Gender?,
        measurementDate: LocalDate = LocalDate.now()
    ): BigDecimal {
        val ageDays = getAgeInDays(birthDate, measurementDate)
        val heightKey = heightCm.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).toInt()
        val isMale = gender != Gender.FEMALE

        val map = if (ageDays < 730) {
            if (isMale) wflBoys else wflGirls
        } else {
            if (isMale) wfhBoys else wfhGirls
        }

        val params = map[heightKey] ?: map.minByOrNull { abs(it.key - heightKey) }?.value ?: LmsParams(1.0, 1.0, 1.0)
        return computeZscore(weightKg.toDouble(), params)
    }

    private fun getAgeInDays(birthDate: LocalDate, measurementDate: LocalDate = LocalDate.now()): Int {
        val days = ChronoUnit.DAYS.between(birthDate, measurementDate).toInt()
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
        // Fallback to nearest age day if exact key not found
        return map[ageDays] ?: map.minByOrNull { kotlin.math.abs(it.key - ageDays) }?.value ?: LmsParams(1.0, 1.0, 1.0)
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

    /**
     * Converts a Z-score to an exact Percentile (0 to 100) using a standard normal CDF approximation.
     */
    fun zScoreToPercentile(zScore: BigDecimal?): BigDecimal? {
        if (zScore == null) return null
        val z = zScore.toDouble()
        val b1 = 0.319381530
        val b2 = -0.356563782
        val b3 = 1.781477937
        val b4 = -1.821255978
        val b5 = 1.330274429
        val p = 0.2316419
        val c = 0.39894228

        val zAbs = abs(z)
        val t = 1.0 / (1.0 + p * zAbs)
        val exp = exp(-zAbs * zAbs / 2.0)
        val probability = 1.0 - c * exp * t * (t * (t * (t * (t * b5 + b4) + b3) + b2) + b1)

        val cdf = if (z > 0) probability else 1.0 - probability
        return BigDecimal(cdf * 100).setScale(2, RoundingMode.HALF_UP)
    }
}
