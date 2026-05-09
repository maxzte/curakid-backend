package com.misterio.curakid.reports

import com.misterio.curakid.children.ChildRepository
import com.misterio.curakid.common.currentUser
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class ReportController(
    private val reportService: ReportService,
    private val childRepository: ChildRepository,
    private val periodSummaryRepository: PeriodSummaryRepository
) {
    /** GET /api/children/{childId}/reports — list all reports for a child */
    @GetMapping("/api/children/{childId}/reports")
    fun getReports(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<ReportResponse>> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.ok(reportService.getReports(child))
    }

    /** GET /api/reports/{reportId} — get a single report */
    @GetMapping("/api/reports/{reportId}")
    fun getReport(
        @PathVariable reportId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        val report = reportService.getReport(reportId)
        // Verify ownership via child
        childRepository.findByIdAndUser(report.childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Report not found") }
        return ResponseEntity.ok(report)
    }

    /** POST /api/reports/{summaryId}/regenerate — re-generate report for a closed period */
    @PostMapping("/api/reports/{summaryId}/regenerate")
    fun regenerateReport(
        @PathVariable summaryId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        val summary = periodSummaryRepository.findById(summaryId)
            .orElseThrow { NoSuchElementException("Period summary not found") }
        // Verify ownership
        childRepository.findByIdAndUser(summary.child!!.id!!, auth.currentUser())
            .orElseThrow { NoSuchElementException("Period summary not found") }
        return ResponseEntity.ok(reportService.regenerateReport(summaryId))
    }
}
