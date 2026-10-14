package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.feature.reports.ReportController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ReportControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var reportService: ReportService
    private val user = User(email = "test@test.com", passwordHash = "hash", name = "Test").apply {
        id = UUID.randomUUID()
    }
    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())
    private val childId = UUID.randomUUID()
    private val reportId = UUID.randomUUID()
    private val summaryId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        reportService = mock()
        val controller = ReportController(reportService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET child reports returns list`() {
        whenever(reportService.getReports(childId, user)).thenReturn(
            listOf(ReportResponse(reportId, childId, null, null, null, null, null, false, Instant.now()))
        )

        mockMvc.perform(get("/api/children/$childId/reports").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(reportId.toString()))
    }

    @Test
    fun `GET report by id returns it`() {
        whenever(reportService.getReport(reportId, user)).thenReturn(
            ReportResponse(reportId, childId, null, null, BigDecimal("3.5"), "Report text", null, false, Instant.now())
        )

        mockMvc.perform(get("/api/reports/$reportId").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.urgencyScore").value(3.5))
    }

    @Test
    fun `GET report status returns boolean`() {
        whenever(reportService.getReportStatus(reportId, user)).thenReturn(ResponseEntity.ok(true))

        mockMvc.perform(get("/api/reports/$reportId/status").principal(auth))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET and POST generate report returns 202 and triggers generation`() {
        mockMvc.perform(get("/api/children/$childId/reports/generate").principal(auth))
            .andExpect(status().isAccepted)

        verify(reportService).triggerPeriodReportGeneration(childId, user)

        mockMvc.perform(post("/api/children/$childId/reports/generate").principal(auth))
            .andExpect(status().isAccepted)
    }

    @Test
    fun `POST regenerate report returns 202 and triggers regeneration`() {
        mockMvc.perform(
            post("/api/reports/$summaryId/regenerate")
                .contentType(MediaType.APPLICATION_JSON)
                .principal(auth)
        )
            .andExpect(status().isAccepted)

        verify(reportService).triggerReportRegeneration(summaryId, user)
    }
}
