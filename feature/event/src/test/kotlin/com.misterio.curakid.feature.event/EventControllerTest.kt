package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.event.EventStatus
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineType
import com.misterio.curakid.feature.event.EventController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class EventControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var eventService: EventService
    private val user = User(email = "test@test.com", passwordHash = "hash", name = "Test").apply {
        id = UUID.randomUUID()
    }
    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())
    private val childId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        eventService = mock()
        val controller = EventController(eventService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET events returns list`() {
        whenever(eventService.getEventsForChild(childId, user)).thenReturn(
            listOf(EventResponse(childId, eventId, EventType.DOCTOR, Instant.now(), "Checkup", null, null, EventStatus.PLANNED))
        )

        mockMvc.perform(get("/api/children/$childId/events").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("Checkup"))
    }

    @Test
    fun `GET vaccine events returns list`() {
        whenever(eventService.getVaccineEventsFor(childId, user)).thenReturn(
            listOf(VaccineEventResponse(childId, eventId, EventType.VACCINE, Instant.now(), EventStatus.PLANNED, null, "BCG", null, 1, 1, null, null, VaccineType.VACCINATION, null))
        )

        mockMvc.perform(get("/api/children/$childId/events/vaccines").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("BCG"))
    }

    @Test
    fun `GET single event returns it`() {
        whenever(eventService.getEvent(eventId, user)).thenReturn(
            EventResponse(childId, eventId, EventType.DOCTOR, Instant.now(), "Checkup", null, null, EventStatus.PLANNED)
        )

        mockMvc.perform(get("/api/children/$childId/events/$eventId").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Checkup"))
    }

    @Test
    fun `POST event returns 201`() {
        whenever(eventService.createEvent(any(), any())).thenReturn(
            EventResponse(childId, eventId, EventType.DOCTOR, Instant.now(), "New Visit", null, null, EventStatus.PLANNED)
        )

        mockMvc.perform(
            post("/api/children/$childId/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"$eventId","childId":"$childId","title":"New Visit","type":"DOCTOR","date":"2026-06-18T10:00:00Z","status":"PLANNED"}""")
                .principal(auth)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("New Visit"))
    }

    @Test
    fun `POST batch events returns 201`() {
        whenever(eventService.createEvents(any(), any())).thenReturn(
            listOf(EventResponse(childId, eventId, EventType.DOCTOR, Instant.now(), "Batch Event", null, null, EventStatus.PLANNED))
        )

        mockMvc.perform(
            post("/api/children/$childId/events/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""[{"id":"$eventId","childId":"$childId","title":"Batch Event","type":"DOCTOR","date":"2026-06-18T10:00:00Z","status":"PLANNED"}]""")
                .principal(auth)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$[0].title").value("Batch Event"))
    }

    @Test
    fun `PUT event returns updated`() {
        whenever(eventService.updateEvent(any(), any(), any())).thenReturn(
            EventResponse(childId, eventId, EventType.DOCTOR, Instant.now(), "Updated Event", null, null, EventStatus.DONE)
        )

        mockMvc.perform(
            put("/api/children/$childId/events/$eventId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Updated Event","status":"DONE"}""")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("DONE"))
    }

    @Test
    fun `DELETE event returns 204`() {
        mockMvc.perform(delete("/api/children/$childId/events/$eventId").principal(auth))
            .andExpect(status().isNoContent())

        verify(eventService).deleteEvent(eventId, user)
    }
}
