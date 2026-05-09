package com.misterio.curakid.reports

import com.misterio.curakid.children.Child
import com.misterio.curakid.common.BaseEntity
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

    @Column(name = "period_label", nullable = false)
    var periodLabel: String = "", // e.g. "0-6 months"

    @Column(name = "is_closed", nullable = false)
    var isClosed: Boolean = false
) : BaseEntity()
