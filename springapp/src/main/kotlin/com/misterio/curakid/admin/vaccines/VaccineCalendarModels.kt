package com.misterio.curakid.admin.vaccines

import com.misterio.curakid.core.database.event.VaccineType

data class ParsedVaccine(
    val name: String,
    val disease: String,
    val doseNumber: Int,
    val totalDoses: Int,
    val type: VaccineType,
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val notes: String? = null
)

data class VaccineDiffItem(
    val name: String,
    val disease: String,
    val doseNumber: Int,
    val totalDoses: Int,
    val type: String,
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val notes: String? = null
)

data class VaccineChangePair(
    val existingId: java.util.UUID,
    val current: VaccineDiffItem,
    val proposed: VaccineDiffItem
)

data class VaccineSyncPreviewResponse(
    val newVaccines: List<VaccineDiffItem>,
    val changedVaccines: List<VaccineChangePair>,
    val missingVaccines: List<VaccineDiffItem>
)

data class ApplyVaccineSyncRequest(
    val toAdd: List<VaccineDiffItem> = emptyList(),
    val toUpdate: List<VaccineDiffItem> = emptyList()
)

data class BackfillSummaryResponse(
    val totalChildren: Int,
    val createdEventsCount: Int,
    val skippedEventsCount: Int,
    val plannedEventsCount: Int
)
