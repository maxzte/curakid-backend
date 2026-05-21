package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.currentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/children/{childId}/events")
class EventController(
    private val eventService: EventService,
) {

    @GetMapping
    fun getEventsForChild(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<EventResponse>> {
        return ResponseEntity.ok(eventService.getEventsForChild(childId, auth.currentUser()))
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
        @PathVariable eventId: UUID,
        auth: Authentication
    ): ResponseEntity<EventResponse> {
        return ResponseEntity.ok(eventService.getEvent(eventId, auth.currentUser()))
    }

    @PostMapping
    fun createEvent(
        @Valid @RequestBody request: EventRequest,
        auth: Authentication
    ): ResponseEntity<EventResponse> {
        return ResponseEntity.status(201).body(eventService.createEvent(auth.currentUser(), request))
    }

    @PostMapping("/batch")
    fun createEvents(
        @Valid @RequestBody requests: List<EventRequest>,
        auth: Authentication
    ): ResponseEntity<List<EventResponse>> {
        return ResponseEntity.status(201).body(eventService.createEvents(auth.currentUser(), requests))
    }

    @PutMapping("/{eventId}")
    fun updateEvent(
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: UpdateEventRequest,
        auth: Authentication
    ): ResponseEntity<EventResponse> {
        return ResponseEntity.ok(eventService.updateEvent(eventId, auth.currentUser(), request))
    }


    @DeleteMapping("/{eventId}")
    fun deleteEvent(
        @PathVariable eventId: UUID,
        auth: Authentication
    ): ResponseEntity<Void> {
        eventService.deleteEvent(eventId, auth.currentUser())
        return ResponseEntity.noContent().build()
    }

}