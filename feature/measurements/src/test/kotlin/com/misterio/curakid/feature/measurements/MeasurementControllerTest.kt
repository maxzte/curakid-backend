package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.essential.common.PagedResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.Instant
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
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
            .build()
    }

    @Test
    fun `GET measurements returns paginated list`() {
        val response = MeasurementResponse(UUID.randomUUID(), childId, BigDecimal("8.5"), null, null, null, null, null, null, null, null, null, null, null, null, Instant.now(), null)
        val page = PageImpl(listOf(response))
        val pagedResponse = PagedResponse(page.content, page.number, page.size, page.totalElements, page.totalPages, page.isLast)
        whenever(measurementService.getMeasurements(eq(childId), eq(user), any<Pageable>())).thenReturn(pagedResponse)

        mockMvc.perform(get("/api/v1/children/$childId/measurements").principal(auth))
            .andExpect(status().isOk)
    }

    @Test
    fun `POST measurement returns 201`() {
        val measurementId = UUID.randomUUID()
        whenever(measurementService.addMeasurement(any<UUID>(), any<User>(), any())).thenReturn(
            MeasurementResponse(measurementId, childId, BigDecimal("8.5"), null, null, null, null, null, null, null, null, null, null, null, null, Instant.now(), null)
        )

        mockMvc.perform(
            post("/api/v1/children/$childId/measurements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"$measurementId","weightKg":8.5,"measurementDate":"2026-06-18T10:00:00Z"}""")
                .principal(auth)
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `PUT measurement returns 200 when ID matches`() {
        val measurementId = UUID.randomUUID()
        whenever(measurementService.addMeasurement(any<UUID>(), any<User>(), any())).thenReturn(
            MeasurementResponse(measurementId, childId, BigDecimal("9.0"), null, null, null, null, null, null, null, null, null, null, null, null, Instant.now(), null)
        )

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/children/$childId/measurements/$measurementId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"$measurementId","weightKg":9.0,"measurementDate":"2026-06-18T10:00:00Z"}""")
                .principal(auth)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `PUT measurement returns 400 when ID mismatches`() {
        val measurementId = UUID.randomUUID()
        val otherId = UUID.randomUUID()

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/children/$childId/measurements/$measurementId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"$otherId","weightKg":9.0,"measurementDate":"2026-06-18T10:00:00Z"}""")
                .principal(auth)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE measurement returns 204`() {
        val measurementId = UUID.randomUUID()

        mockMvc.perform(
            MockMvcRequestBuilders.delete("/api/v1/children/$childId/measurements/$measurementId")
                .principal(auth)
        )
            .andExpect(status().isNoContent)

        verify(measurementService).deleteMeasurement(eq(childId), eq(measurementId), eq(user))
    }
}
