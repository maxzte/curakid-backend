package com.misterio.curakid.reports

import com.misterio.curakid.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface PeriodSummaryRepository : JpaRepository<PeriodSummary, UUID> {
    fun findTopByChildOrderByCreatedAtDesc(child: Child): Optional<PeriodSummary>
    fun findAllByChildOrderByCreatedAtDesc(child: Child): List<PeriodSummary>
}

interface AiReportRepository : JpaRepository<AiReport, UUID> {
    fun findAllByChildOrderByCreatedAtDesc(child: Child): List<AiReport>
    fun findByPeriodSummary(periodSummary: PeriodSummary): Optional<AiReport>
}
