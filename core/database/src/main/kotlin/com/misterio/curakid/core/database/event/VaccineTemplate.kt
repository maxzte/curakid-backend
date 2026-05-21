package com.misterio.curakid.core.database.event

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "vaccine_templates")
class VaccineTemplate(

    @Column(nullable = false)
    var name: String = "",

    @Column
    var disease: String = "",

    @Column(nullable = true)
    var doseNumber: Int? = null,

    @Column(nullable = true)
    var totalDoses: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column
    var type: VaccineType = VaccineType.VACCINATION,

    @Column
    var scheduledAgeMonths: Int? = null,

    @Column
    var scheduledAgeDays: Int? = null,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null

) : BaseEntity()

enum class VaccineType {
    VACCINATION,
    REVACCINATION,
}
