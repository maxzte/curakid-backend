package com.misterio.curakid.reports

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

// --- Responses ---
data class ReportResponse(
    val id: UUID,
    val childId: UUID,
    val urgencyScore: BigDecimal?,
    val reportText: String?,
    val recommendations: String?,
    val isFallback: Boolean,
    val periodLabel: String?,
    val createdAt: LocalDateTime?
) {
    companion object {
        fun from(report: AiReport): ReportResponse = ReportResponse(
            id = report.id!!,
            childId = report.child!!.id!!,
            urgencyScore = report.urgencyScore,
            reportText = report.reportText,
            recommendations = report.recommendations,
            isFallback = report.isFallback,
            periodLabel = report.periodSummary?.periodLabel,
            createdAt = report.createdAt
        )
    }
}

data class PeriodSummaryResponse(
    val id: UUID,
    val childId: UUID,
    val periodLabel: String,
    val periodStart: java.time.LocalDate,
    val periodEnd: java.time.LocalDate?,
    val isClosed: Boolean
) {
    companion object {
        fun from(ps: PeriodSummary): PeriodSummaryResponse = PeriodSummaryResponse(
            id = ps.id!!,
            childId = ps.child!!.id!!,
            periodLabel = ps.periodLabel,
            periodStart = ps.periodStart,
            periodEnd = ps.periodEnd,
            isClosed = ps.isClosed
        )
    }
}
