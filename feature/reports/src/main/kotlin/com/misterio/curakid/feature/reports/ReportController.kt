package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.currentUser
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class ReportController(
    private val reportService: ReportService
) {
    /** GET /api/children/{childId}/reports — list all reports for a child */
    @GetMapping("/api/children/{childId}/reports")
    fun getReports(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<ReportResponse>> {
        return ResponseEntity.ok(reportService.getReports(childId, auth.currentUser()))
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

    @GetMapping("/api/reports/{reportId}/status")
    fun getReportStatus(
        @PathVariable reportId: UUID,
        auth: Authentication
    ): ResponseEntity<Boolean> = reportService.getReportStatus(reportId, auth.currentUser())

    @RequestMapping(value = ["/api/children/{childId}/reports/generate"], method = [RequestMethod.GET, RequestMethod.POST])
    fun generateReport(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        reportService.triggerPeriodReportGeneration(childId, auth.currentUser())
        return ResponseEntity.accepted().build()
    }

    /** POST /api/reports/{summaryId}/regenerate — re-generate report for a closed period */
    @PostMapping("/api/reports/{summaryId}/regenerate")
    fun regenerateReport(
        @PathVariable summaryId: UUID,
        auth: Authentication
    ): ResponseEntity<ReportResponse> {
        reportService.triggerReportRegeneration(summaryId, auth.currentUser())
        return ResponseEntity.accepted().build()  // Return 202 for async operation
    }
}
