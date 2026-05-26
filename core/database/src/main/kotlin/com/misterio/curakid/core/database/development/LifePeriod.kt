package com.misterio.curakid.core.database.development

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.question.Question
import jakarta.persistence.*

// https://zakon.rada.gov.ua/laws/show/z1936-24#n72
@Entity
@Table(name = "life_periods")
class LifePeriod(

    @Column(nullable = false, unique = true)
    var code: String = "",

    @Column(nullable = false)
    var label: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var description: String = "",

    @Column(name = "min_months", nullable = false)
    var minMonths: Int = 0,

    @Column(name = "max_months", nullable = false)
    var maxMonths: Int = 0,

    @OneToMany(mappedBy = "lifePeriod", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @OrderBy("weight ASC")
    val questions: MutableList<Question> = mutableListOf()

) : BaseEntity()

