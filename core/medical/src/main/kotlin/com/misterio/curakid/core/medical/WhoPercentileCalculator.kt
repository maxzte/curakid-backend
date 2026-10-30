package com.misterio.curakid.core.medical

import com.misterio.curakid.core.database.children.Gender
import jakarta.annotation.PostConstruct
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Provides WHO Percentile data for Weight-for-Age, Height-for-Age, BMI-for-Age, and Head Circumference-for-Age.
 *
 * Loads full WHO Percentile tables from resources on startup.
 *
 * References: https://www.who.int/tools/child-growth-standards
 */
@Component
class WhoPercentileCalculator {

    data class PercentileParams(
        val p01: Double,
        val p1: Double,
        val p3: Double,
        val p5: Double,
        val p10: Double,
        val p15: Double,
        val p25: Double,
        val p50: Double,
        val p75: Double,
        val p85: Double,
        val p90: Double,
        val p95: Double,
        val p97: Double,
        val p99: Double,
        val p999: Double
    )

    private val wfaBoys = mutableMapOf<Int, PercentileParams>()
    private val wfaGirls = mutableMapOf<Int, PercentileParams>()
    private val hfaBoys = mutableMapOf<Int, PercentileParams>()
    private val hfaGirls = mutableMapOf<Int, PercentileParams>()
    private val wflBoys = mutableMapOf<Int, PercentileParams>()
    private val wflGirls = mutableMapOf<Int, PercentileParams>()
    private val wfhBoys = mutableMapOf<Int, PercentileParams>()
    private val wfhGirls = mutableMapOf<Int, PercentileParams>()
    private val bmiBoys = mutableMapOf<Int, PercentileParams>()
    private val bmiGirls = mutableMapOf<Int, PercentileParams>()
    private val hcfaBoys = mutableMapOf<Int, PercentileParams>()
    private val hcfaGirls = mutableMapOf<Int, PercentileParams>()

    @PostConstruct
    fun init() {
        loadPercentileData("precentile/wfa-e-boys.xlsx", wfaBoys)
        loadPercentileData("precentile/wfa-e-girls.xlsx", wfaGirls)
        loadPercentileData("precentile/lhfa-e-boys.xlsx", hfaBoys)
        loadPercentileData("precentile/lhfa-e-girls.xlsx", hfaGirls)
        loadWfhPercentileData("precentile/wfl-e-boys.xlsx", wflBoys)
        loadWfhPercentileData("precentile/wfl-e-girls.xlsx", wflGirls)
        loadWfhPercentileData("precentile/wfh-e-boys.xlsx", wfhBoys)
        loadWfhPercentileData("precentile/wfh-e-girls.xlsx", wfhGirls)
        loadPercentileData("precentile/bfa-e-boys.xlsx", bmiBoys)
        loadPercentileData("precentile/bfa-e-girls.xlsx", bmiGirls)
        loadPercentileData("precentile/hcfa-e-boys.xlsx", hcfaBoys)
        loadPercentileData("precentile/hcfa-e-girls.xlsx", hcfaGirls)
    }

    private fun extractPercentileParams(row: org.apache.poi.ss.usermodel.Row): PercentileParams {
        val p01 = row.getCell(4)?.numericCellValue ?: 0.0
        val p1 = row.getCell(5)?.numericCellValue ?: 0.0
        val p3 = row.getCell(6)?.numericCellValue ?: 0.0
        val p5 = row.getCell(7)?.numericCellValue ?: 0.0
        val p10 = row.getCell(8)?.numericCellValue ?: 0.0
        val p15 = row.getCell(9)?.numericCellValue ?: 0.0
        val p25 = row.getCell(10)?.numericCellValue ?: 0.0
        val p50 = row.getCell(11)?.numericCellValue ?: 0.0
        val p75 = row.getCell(12)?.numericCellValue ?: 0.0
        val p85 = row.getCell(13)?.numericCellValue ?: 0.0
        val p90 = row.getCell(14)?.numericCellValue ?: 0.0
        val p95 = row.getCell(15)?.numericCellValue ?: 0.0
        val p97 = row.getCell(16)?.numericCellValue ?: 0.0
        val p99 = row.getCell(17)?.numericCellValue ?: 0.0
        val p999 = row.getCell(18)?.numericCellValue ?: 0.0

        return PercentileParams(
            p01, p1, p3, p5, p10, p15, p25, p50,
            p75, p85, p90, p95, p97, p99, p999
        )
    }

    private fun loadWfhPercentileData(path: String, map: MutableMap<Int, PercentileParams>) {
        ClassPathResource(path).inputStream.use { inputStream ->
            WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                for (row in sheet) {
                    if (row.rowNum == 0) continue // Skip header
                    val heightCell = row.getCell(0) ?: continue

                    try {
                        val heightKey = (heightCell.numericCellValue * 10).roundToInt()
                        map[heightKey] = extractPercentileParams(row)
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }
    }

    private fun loadPercentileData(path: String, map: MutableMap<Int, PercentileParams>) {
        ClassPathResource(path).inputStream.use { inputStream ->
            WorkbookFactory.create(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                for (row in sheet) {
                    if (row.rowNum == 0) continue // Skip header
                    val dayCell = row.getCell(0) ?: continue

                    try {
                        val day = dayCell.numericCellValue.toInt()
                        map[day] = extractPercentileParams(row)
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }
    }

    /**
     * Returns the full map of percentile params for drawing reference curves on the client.
     */
    fun getRawMap(type: ChartType, gender: Gender?): Map<Int, PercentileParams> {
        val isMale = gender != Gender.FEMALE
        return when (type) {
            ChartType.WFA -> if (isMale) wfaBoys else wfaGirls
            ChartType.HFA -> if (isMale) hfaBoys else hfaGirls
            ChartType.BMI -> if (isMale) bmiBoys else bmiGirls
            ChartType.HCFA -> if (isMale) hcfaBoys else hcfaGirls
            ChartType.WFH -> if (isMale) wfhBoys else wfhGirls
            ChartType.WFL -> if (isMale) wflBoys else wflGirls
        }
    }

    fun getParamsForAge(type: ChartType, gender: Gender?, ageDays: Int): PercentileParams? {
        val map = getRawMap(type, gender)
        val day = ageDays.coerceIn(0, 1856)
        return map[day] ?: map.minByOrNull { abs(it.key - day) }?.value
    }

    /**
     * Returns percentile params for weight-for-height/length.
     * Uses WFL for children < 2 years (730 days) and WFH for older children.
     */
    fun getParamsForHeight(gender: Gender?, heightCm: Double, ageDays: Int): PercentileParams? {
        val isMale = gender != Gender.FEMALE
        val map = if (ageDays < 730) {
            if (isMale) wflBoys else wflGirls
        } else {
            if (isMale) wfhBoys else wfhGirls
        }

        val heightKey = (heightCm * 10).roundToInt()
        return map[heightKey] ?: map.minByOrNull { abs(it.key - heightKey) }?.value
    }

    /**
     * Builds standard percentile norms map (P3, P15, P50, P85, P97) for child anthropometry.
     */
    fun buildNormsMap(
        gender: Gender?,
        ageDays: Int,
        heightCm: Double? = null,
        keyPrefix: String = "WHO"
    ): Map<String, Map<String, Double>> {
        val res = mutableMapOf<String, Map<String, Double>>()

        fun addNorms(variable: String, type: ChartType) {
            getParamsForAge(type, gender, ageDays)?.let { p ->
                res[variable] = mapOf(
                    "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                )
            }
        }

        if (keyPrefix == "WHO") {
            addNorms("WFA (kg)", ChartType.WFA)
            addNorms("HFA (cm)", ChartType.HFA)
            addNorms("BMI", ChartType.BMI)
            addNorms("HCFA (cm)", ChartType.HCFA)
            heightCm?.let { h ->
                getParamsForHeight(gender, h, ageDays)?.let { p ->
                    res["WFH (kg)"] = mapOf(
                        "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                    )
                }
            }
        } else {
            addNorms("wfaZscore", ChartType.WFA)
            addNorms("hfaZscore", ChartType.HFA)
            addNorms("bmiZscore", ChartType.BMI)
            addNorms("hcfaZscore", ChartType.HCFA)
            heightCm?.let { h ->
                getParamsForHeight(gender, h, ageDays)?.let { p ->
                    res["wfhZscore"] = mapOf(
                        "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                    )
                }
            }
        }

        return res
    }
}
