package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.reports.AiReport
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// --- Charts & Norms ---
data class ChartNormPoint(
    val x: Double, // age in days OR height in cm (for WFH)
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

// --- Requests ---
data class AddMeasurementRequest(
    @field:DecimalMin("0.1") val weightKg: BigDecimal?,
    @field:DecimalMin("0.1") val heightCm: BigDecimal?,
    @field:DecimalMin("0.1") val headCircCm: BigDecimal?,
    @field:NotNull val measurementDate: LocalDateTime
)

// --- Responses ---
data class MeasurementResponse(
    val id: UUID,
    val childId: UUID,
    val weightKg: BigDecimal?,
    val heightCm: BigDecimal?,
    val headCircCm: BigDecimal?,
    val wfaZscore: BigDecimal?,
    val hfaZscore: BigDecimal?,
    val bmiZscore: BigDecimal?,
    val hcfaZscore: BigDecimal?,
    val wfhZscore: BigDecimal?,
    val wfaPercentile: BigDecimal?,
    val hfaPercentile: BigDecimal?,
    val bmiPercentile: BigDecimal?,
    val hcfaPercentile: BigDecimal?,
    val wfhPercentile: BigDecimal?,
    val measurementDate: LocalDateTime,
    val aiReportId: UUID?,
) {
    companion object {
        fun from(m: Measurement, aiReportId: UUID?): MeasurementResponse = MeasurementResponse(
            id = m.id!!,
            childId = m.child!!.id!!,
            weightKg = m.weightKg,
            heightCm = m.heightCm,
            headCircCm = m.headCircCm,
            wfaZscore = m.wfaZscore,
            hfaZscore = m.hfaZscore,
            bmiZscore = m.bmiZscore,
            hcfaZscore = m.hcfaZscore,
            wfhZscore = m.wfhZscore,
            wfaPercentile = m.wfaPercentile,
            hfaPercentile = m.hfaPercentile,
            bmiPercentile = m.bmiPercentile,
            hcfaPercentile = m.hcfaPercentile,
            wfhPercentile = m.wfhPercentile,
            measurementDate = m.measurementDate,
            aiReportId = aiReportId,
        )
    }
}
