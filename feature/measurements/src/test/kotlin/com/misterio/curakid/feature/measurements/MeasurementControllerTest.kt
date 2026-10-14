package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.measurements.MeasurementWithReportProjection
import com.misterio.curakid.feature.measurements.MeasurementController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class MeasurementControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var measurementService: MeasurementService
    private val user = User(email = "test@test.com", passwordHash = "hash", name = "Test").apply {
        id = UUID.randomUUID()
    }
    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())
    private val childId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        measurementService = mock()
        val controller = MeasurementController(measurementService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET measurements returns list`() {
        val projection = mock<MeasurementWithReportProjection>()
        whenever(projection.id).thenReturn(UUID.randomUUID())
        whenever(projection.childId).thenReturn(childId)
        whenever(projection.weightKg).thenReturn(BigDecimal("8.5"))

        whenever(measurementService.getMeasurements(childId, user)).thenReturn(listOf(projection))

        mockMvc.perform(get("/api/children/$childId/measurements").principal(auth))
            .andExpect(status().isOk)
    }

    @Test
    fun `POST measurement returns 201`() {
        val measurementId = UUID.randomUUID()
        whenever(measurementService.addMeasurement(any<UUID>(), any<User>(), any())).thenReturn(
            MeasurementResponse(measurementId, childId, BigDecimal("8.5"), null, null, null, null, null, null, null, null, null, null, null, null, Instant.now(), null)
        )

        mockMvc.perform(
            post("/api/children/$childId/measurements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"$measurementId","weightKg":8.5,"measurementDate":"2026-06-18T10:00:00Z"}""")
                .principal(auth)
        )
            .andExpect(status().isCreated)
    }
}
