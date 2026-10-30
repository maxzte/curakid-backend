package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.currentUser
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class ReportController(
    private val reportService: ReportService
) {
    /** GET /api/v1/children/{childId}/reports — list all reports for a child */
    @GetMapping("/children/{childId}/reports")
    fun getReports(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<ReportResponse>> {
        return ResponseEntity.ok(reportService.getReports(childId, auth.currentUser()))
    }

    /** GET /api/v1/reports/{reportId} — get a single report */
    @GetMapping("/reports/{reportId}")
    fun getReport(
        @PathVariable reportId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        val report = reportService.getReport(reportId, auth.currentUser())
        return ResponseEntity.ok(report)
    }

    @GetMapping("/reports/{reportId}/status")
    fun getReportStatus(
        @PathVariable reportId: UUID,
        auth: Authentication
    ): ResponseEntity<Boolean> = reportService.getReportStatus(reportId, auth.currentUser())

    @PostMapping("/children/{childId}/reports/generate")
    fun generateReport(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        reportService.triggerPeriodReportGeneration(childId, auth.currentUser())
        return ResponseEntity.accepted().build()
    }

    /** POST /api/v1/reports/{summaryId}/regenerate — re-generate report for a closed period */
    @PostMapping("/reports/{summaryId}/regenerate")
    fun regenerateReport(
        @PathVariable summaryId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        reportService.triggerReportRegeneration(summaryId, auth.currentUser())
        return ResponseEntity.accepted().build()  // Return 202 for async operation
    }
}
