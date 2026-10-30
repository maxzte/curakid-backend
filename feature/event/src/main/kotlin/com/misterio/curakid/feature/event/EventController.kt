package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.core.essential.common.PagedResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

@RestController
@RequestMapping("/api/v1/children/{childId}/events")
class EventController(
    private val eventService: EventService,
) {

    @GetMapping
    fun getEventsForChild(
        @PathVariable childId: UUID,
        @RequestParam(required = false) startDate: Instant?,
        @RequestParam(required = false) endDate: Instant?,
        pageable: Pageable,
        auth: Authentication
    ): ResponseEntity<PagedResponse<EventResponse>> {
        return ResponseEntity.ok(eventService.getEventsForChild(childId, auth.currentUser(), startDate, endDate, pageable))
    }

    @GetMapping("/vaccines")
    fun getVaccineEventsFor(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<VaccineEventResponse>> {
        return ResponseEntity.ok(eventService.getVaccineEventsFor(childId, auth.currentUser()))
    }

    @GetMapping("/{eventId}")
    fun getEvent(
        @PathVariable childId: UUID,
        @PathVariable eventId: UUID,
        auth: Authentication
    ): ResponseEntity<EventResponse> {
        return ResponseEntity.ok(eventService.getEvent(childId, eventId, auth.currentUser()))
    }

    @PostMapping
    fun createEvent(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: EventRequest,
        auth: Authentication
    ): ResponseEntity<EventResponse> {
        if (request.childId != childId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "childId in path ($childId) does not match childId in request body (${request.childId})"
            )
        }
        return ResponseEntity.status(201).body(eventService.createEvent(auth.currentUser(), request))
    }

    @PostMapping("/batch")
    fun createEvents(
        @PathVariable childId: UUID,
        @Valid @RequestBody requests: List<EventRequest>,
        auth: Authentication
    ): ResponseEntity<List<EventResponse>> {
        val mismatch = requests.firstOrNull { it.childId != childId }
        if (mismatch != null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "childId in path ($childId) does not match childId in item ${mismatch.id} (${mismatch.childId})"
            )
        }
        return ResponseEntity.status(201).body(eventService.createEvents(auth.currentUser(), requests))
    }

    @PutMapping("/{eventId}")
    fun updateEvent(
        @PathVariable childId: UUID,
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: UpdateEventRequest,
        auth: Authentication
    ): ResponseEntity<EventResponse> {
        return ResponseEntity.ok(eventService.updateEvent(childId, eventId, auth.currentUser(), request))
    }


    @DeleteMapping("/{eventId}")
    fun deleteEvent(
        @PathVariable childId: UUID,
        @PathVariable eventId: UUID,
        auth: Authentication
    ): ResponseEntity<Void> {
        eventService.deleteEvent(childId, eventId, auth.currentUser())
        return ResponseEntity.noContent().build()
    }

}