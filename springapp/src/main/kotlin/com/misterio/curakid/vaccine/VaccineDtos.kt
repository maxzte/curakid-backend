package com.misterio.curakid.vaccine

import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.*

data class UpdateVaccineRequest(
    @field:NotNull val status: VaccineStatus,
    val administeredDate: LocalDate?,
    val batchNumber: String?,
    val administeredBy: String?,
    val notes: String?
)

data class VaccineResponse(
    val id: UUID,
    val name: String,
    val disease: String,
    val doseNumber: Int,
    val totalDoses: Int,
    val type: VaccineType,
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val administeredDate: LocalDate?,
    val batchNumber: String?,
    val administeredBy: String?,
    val notes: String?,
    val status: VaccineStatus
)