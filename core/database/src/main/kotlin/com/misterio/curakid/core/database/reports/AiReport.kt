package com.misterio.curakid.core.database.reports

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.measurements.Measurement
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "ai_reports")
class AiReport(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_summary_id")
    var periodSummary: PeriodSummary? = null,

    @Column(name = "urgency_score", precision = 3, scale = 2)
    var urgencyScore: BigDecimal? = null,

    @Column(name = "report_text", columnDefinition = "TEXT")
    var reportText: String? = null,

    @Column(name = "recommendations", columnDefinition = "TEXT")
    var recommendations: String? = null,

    @Column(name = "is_fallback", nullable = false)
    var isFallback: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_measurement_id")
    var triggerMeasurement: Measurement? = null,

) : BaseEntity()
