package com.misterio.curakid.admin.vaccines

import com.misterio.curakid.core.database.event.VaccineTemplate
import com.misterio.curakid.core.database.event.VaccineTemplateRepository
import org.springframework.stereotype.Service

@Service
class VaccineCalendarDiffService(
    private val vaccineTemplateRepository: VaccineTemplateRepository
) {
    fun calculateDiff(parsedList: List<ParsedVaccine>): VaccineSyncPreviewResponse {
        val existingTemplates = vaccineTemplateRepository.findAllByIsActiveTrue()

        // Key to identify unique template: (name, type, doseNumber)
        val existingMap = existingTemplates.associateBy {
            Triple(it.name.trim().lowercase(), it.type.name, it.doseNumber ?: 1)
        }

        val parsedMap = parsedList.associateBy {
            Triple(it.name.trim().lowercase(), it.type.name, it.doseNumber)
        }

        val newItems = mutableListOf<VaccineDiffItem>()
        val changedItems = mutableListOf<VaccineChangePair>()
        val missingItems = mutableListOf<VaccineDiffItem>()

        for ((key, parsed) in parsedMap) {
            val existing = existingMap[key]
            if (existing == null) {
                newItems.add(parsed.toDiffItem())
            } else {
                // Check if attributes changed (age months/days or totalDoses)
                val isChanged = existing.scheduledAgeMonths != parsed.scheduledAgeMonths ||
                        existing.scheduledAgeDays != parsed.scheduledAgeDays ||
                        existing.totalDoses != parsed.totalDoses

                if (isChanged) {
                    val existingId = requireNotNull(existing.id) { "Existing VaccineTemplate ID must not be null" }
                    changedItems.add(
                        VaccineChangePair(
                            existingId = existingId,
                            current = existing.toDiffItem(),
                            proposed = parsed.toDiffItem()
                        )
                    )
                }
            }
        }

        for ((key, existing) in existingMap) {
            if (!parsedMap.containsKey(key)) {
                missingItems.add(existing.toDiffItem())
            }
        }

        return VaccineSyncPreviewResponse(
            newVaccines = newItems,
            changedVaccines = changedItems,
            missingVaccines = missingItems
        )
    }

    private fun ParsedVaccine.toDiffItem() = VaccineDiffItem(
        name = name,
        disease = disease,
        doseNumber = doseNumber,
        totalDoses = totalDoses,
        type = type.name,
        scheduledAgeMonths = scheduledAgeMonths,
        scheduledAgeDays = scheduledAgeDays,
        notes = notes
    )

    private fun VaccineTemplate.toDiffItem() = VaccineDiffItem(
        name = name,
        disease = disease,
        doseNumber = doseNumber ?: 1,
        totalDoses = totalDoses ?: 1,
        type = type.name,
        scheduledAgeMonths = scheduledAgeMonths,
        scheduledAgeDays = scheduledAgeDays,
        notes = notes
    )
}
