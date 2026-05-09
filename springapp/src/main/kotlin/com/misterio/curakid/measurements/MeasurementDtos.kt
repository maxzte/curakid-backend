package com.misterio.curakid.measurements

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// --- Requests ---
data class AddMeasurementRequest(
    @field:DecimalMin("0.1") val weightKg: BigDecimal?,
    @field:DecimalMin("0.1") val heightCm: BigDecimal?,
    val headCircCm: BigDecimal?,
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
    val measurementDate: LocalDateTime
) {
    companion object {
        fun from(m: Measurement): MeasurementResponse = MeasurementResponse(
            id = m.id!!,
            childId = m.child!!.id!!,
            weightKg = m.weightKg,
            heightCm = m.heightCm,
            headCircCm = m.headCircCm,
            wfaZscore = m.wfaZscore,
            hfaZscore = m.hfaZscore,
            bmiZscore = m.bmiZscore,
            measurementDate = m.measurementDate
        )
    }
}
