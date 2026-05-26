package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.NoSuchElementException
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
        val report = reportService.getReport(reportId, auth.currentUser())
        return ResponseEntity.ok(report)
    }

    @GetMapping("api/reports/{reportId}/status")
    fun getReportStatus(@PathVariable reportId: UUID): ResponseEntity<Boolean> = reportService.getReportStatus(reportId)

    @GetMapping("/api/children/{childId}/reports/generate")
    fun generateReport(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.accepted().build()
    }

    /** POST /api/reports/{summaryId}/regenerate — re-generate report for a closed period */
    @PostMapping("/api/reports/{summaryId}/regenerate")
    fun regenerateReport(
        @PathVariable summaryId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        val currentUserId = auth.currentUser().id
        val summary = periodSummaryRepository.findByIdAndChildUser(summaryId, currentUserId)
            .orElseThrow { NoSuchElementException("Period summary not found") }
        return ResponseEntity.accepted().build()  // Return 202 for async operation
    }
}
