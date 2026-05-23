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
    private val wfhBoys = mutableMapOf<Int, PercentileParams>()
    private val wfhGirls = mutableMapOf<Int, PercentileParams>()
    private val bmiBoys = mutableMapOf<Int, PercentileParams>()
    private val bmiGirls = mutableMapOf<Int, PercentileParams>()
    private val hcfaBoys = mutableMapOf<Int, PercentileParams>()
    private val hcfaGirls = mutableMapOf<Int, PercentileParams>()

    @PostConstruct
    fun init() {
        loadPercentileData("who/wfa-e-boys.xlsx", wfaBoys)
        loadPercentileData("who/wfa-e-girls.xlsx", wfaGirls)
        loadPercentileData("who/lhfa-e-boys.xlsx", hfaBoys)
        loadPercentileData("who/lhfa-e-girls.xlsx", hfaGirls)
        loadWfhPercentileData("who/wfh-e-boys.xlsx", wfhBoys)
        loadWfhPercentileData("who/wfh-e-girls.xlsx", wfhGirls)
        loadPercentileData("who/bfa-e-boys.xlsx", bmiBoys)
        loadPercentileData("who/bfa-e-girls.xlsx", bmiGirls)
        loadPercentileData("who/hcfa-e-boys.xlsx", hcfaBoys)
        loadPercentileData("who/hcfa-e-girls.xlsx", hcfaGirls)
    }

    private fun loadWfhPercentileData(path: String, map: MutableMap<Int, PercentileParams>) {
        ClassPathResource(path).inputStream.use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            for (row in sheet) {
                if (row.rowNum == 0) continue // Skip header
                val heightCell = row.getCell(0) ?: continue

                try {
                    val heightKey = (heightCell.numericCellValue * 10).roundToInt()
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

                    map[heightKey] = PercentileParams(
                        p01, p1, p3, p5, p10, p15, p25, p50,
                        p75, p85, p90, p95, p97, p99, p999
                    )
                } catch (e: Exception) {
                    continue
                }
            }
        }
    }

    private fun loadPercentileData(path: String, map: MutableMap<Int, PercentileParams>) {
        ClassPathResource(path).inputStream.use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            for (row in sheet) {
                if (row.rowNum == 0) continue // Skip header
                val dayCell = row.getCell(0) ?: continue

                try {
                    val day = dayCell.numericCellValue.toInt()
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

                    map[day] = PercentileParams(
                        p01, p1, p3, p5, p10, p15, p25, p50,
                        p75, p85, p90, p95, p97, p99, p999
                    )
                } catch (e: Exception) {
                    continue
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
        }
    }

    fun getParamsForAge(type: ChartType, gender: Gender?, ageDays: Int): PercentileParams? {
        val map = getRawMap(type, gender)
        val day = ageDays.coerceIn(0, 1856)
        return map[day] ?: map.minByOrNull { abs(it.key - day) }?.value
    }

    fun getParamsForHeight(gender: Gender?, heightCm: Double): PercentileParams? {
        val map = getRawMap(ChartType.WFH, gender)
        val heightKey = (heightCm * 10).roundToInt()
        return map[heightKey] ?: map.minByOrNull { abs(it.key - heightKey) }?.value
    }
}
