package com.misterio.curakid.development

import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "life_period")
class LifePeriod(

    @Column(nullable = false, unique = true)
    var code: String = "",

    @Column(nullable = false)
    var label: String = "",

    @Column(nullable = false)
    var about: String = "",

    @Column(name = "min_months", nullable = false)
    var minMonths: Int = 0,

    @Column(name = "max_months", nullable = false)
    var maxMonths: Int = 0,

    @OneToMany(mappedBy = "lifePeriod", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @OrderBy("weight ASC")
    val questions: MutableList<Question> = mutableListOf()

) : BaseEntity()

