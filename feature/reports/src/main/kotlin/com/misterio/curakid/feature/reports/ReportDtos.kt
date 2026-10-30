package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.reports.AiReport
import java.math.BigDecimal
import java.time.Instant
import java.util.*

// --- Responses ---
data class ReportResponse(
    val id: UUID,
    val childId: UUID,
    val lifePeriodId: UUID?,
    val triggerMeasurementId: UUID?,
    val urgencyScore: BigDecimal?,
    val reportText: String?,
    val recommendations: String?,
    val isFallback: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(report: AiReport): ReportResponse = ReportResponse(
            id = requireNotNull(report.id) { "AiReport ID must not be null" },
            childId = requireNotNull(report.child?.id) { "Child ID must not be null for AiReport" },
            lifePeriodId = report.periodSummary?.lifePeriod?.id,
            triggerMeasurementId = report.triggerMeasurement?.id,
            urgencyScore = report.urgencyScore,
            reportText = report.reportText,
            recommendations = report.recommendations,
            isFallback = report.isFallback,
            createdAt = report.createdAt ?: Instant.now()
        )
    }
}
