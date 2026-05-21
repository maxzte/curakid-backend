package com.misterio.curakid.core.database.reports

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate

/**
 * Represents a developmental period for a child (e.g. 0-6 months, 6-12 months, 12-18 months, etc.)
 * When a child crosses into a new period, the current PeriodSummary is closed and an AI report is generated.
 */
@Entity
@Table(name = "period_summaries")
class PeriodSummary(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Column(name = "period_start", nullable = false)
    var periodStart: LocalDate = LocalDate.now(),

    @Column(name = "period_end")
    var periodEnd: LocalDate? = null,

    @Column(name = "is_closed", nullable = false)
    var isClosed: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "life_period_id")
    var lifePeriod: LifePeriod? = null
    
) : BaseEntity()
