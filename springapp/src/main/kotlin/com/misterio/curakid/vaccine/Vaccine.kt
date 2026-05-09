package com.misterio.curakid.vaccine

import com.misterio.curakid.children.Child
import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "vaccines")
class Vaccine(

    @Column(nullable = false)
    var name: String = "",

    @Column
    var disease: String = "",  // від якої хвороби

    @Column
    var doseNumber: Int = 1,   // номер дози

    @Column
    var totalDoses: Int = 1,   // всього доз у серії

    @Enumerated(EnumType.STRING)
    @Column
    var type: VaccineType = VaccineType.VACCINATION,

    @Column
    var scheduledAgeMonths: Int? = null,  // рекомендований вік у місяцях

    @Column
    var scheduledAgeDays: Int? = null,    // для новонароджених (день 1, 2)

    @Column
    var notes: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Column
    var administeredDate: LocalDate? = null,  // фактична дата

    @Column
    var batchNumber: String? = null,  // номер серії вакцини

    @Column
    var administeredBy: String? = null,  // лікар/заклад

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: VaccineStatus = VaccineStatus.PLANNED,

) : BaseEntity()

enum class VaccineType {
    VACCINATION,     // перша вакцинація
    REVACCINATION    // ревакцинація
}

enum class VaccineStatus {
    PLANNED,    // заплановано за календарем
    DONE,       // зроблено
    SKIPPED,    // пропущено
    DELAYED     // відкладено
}