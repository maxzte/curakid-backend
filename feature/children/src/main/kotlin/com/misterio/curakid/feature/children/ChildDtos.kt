package com.misterio.curakid.feature.children

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

// --- Requests ---
data class CreateChildRequest(

    @field:NotNull
    val id: UUID,

    @field:NotNull
    @field:NotBlank
    val name: String,

    @field:NotNull
    @field:PastOrPresent
    val birthDate: LocalDate,

    @field:NotNull
    val gender: Gender,

    @field:NotNull
    val isPremature: Boolean,

    @field:Min(20)
    @field:Max(37)
    val gestationalWeeks: Int?,

    val allergies: List<String>?,
    val chronicDiseases: List<String>?,
    val pastDiseases: List<String>?,
    val specialFeatures: List<String>?,
)

data class UpdateChildRequest(
    val name: String?,
    val birthDate: LocalDate?,
    val gender: Gender?,
    val gestationalWeeks: Int?,
    val allergies: List<String>?,
    val chronicDiseases: List<String>?,
    val pastDiseases: List<String>?,
    val specialFeatures: List<String>?,
)

// --- Responses ---
data class ChildResponse(
    val id: UUID,
    val name: String,
    val birthDate: LocalDate,
    val gender: Gender?,
    val isPremature: Boolean,
    val gestationalWeeks: Int?,
    val allergies: List<String> = emptyList(),
    val chronicDiseases: List<String> = emptyList(),
    val pastDiseases: List<String> = emptyList(),
    val specialFeatures: List<String> = emptyList(),
    val ageInMonths: Long
) {
    companion object {
        fun from(child: Child): ChildResponse {
            val ageInMonths = ChronoUnit.MONTHS.between(child.birthDate, LocalDate.now())
            return ChildResponse(
                id = child.id!!,
                name = child.name,
                birthDate = child.birthDate,
                gender = child.gender,
                isPremature = child.isPremature,
                gestationalWeeks = child.gestationalWeeks,
                ageInMonths = ageInMonths,
                allergies = child.allergies,
                chronicDiseases = child.chronicConditions,
                pastDiseases = child.pastIllnesses,
                specialFeatures = child.notes,
            )
        }
    }
}
