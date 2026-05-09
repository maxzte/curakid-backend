package com.misterio.curakid.vaccine

import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "vaccine_templates")
class VaccineTemplate(

    @Column(nullable = false)
    var name: String = "",

    @Column
    var disease: String = "",

    @Column
    var doseNumber: Int = 1,

    @Column
    var totalDoses: Int = 1,

    @Enumerated(EnumType.STRING)
    @Column
    var type: VaccineType = VaccineType.VACCINATION,

    @Column
    var scheduledAgeMonths: Int? = null,

    @Column
    var scheduledAgeDays: Int? = null,

    @Column
    var notes: String? = null

) : BaseEntity()
