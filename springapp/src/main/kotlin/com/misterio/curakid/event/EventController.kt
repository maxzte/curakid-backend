package com.misterio.curakid.event

import com.misterio.curakid.common.currentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventService: EventService
) {

    @GetMapping("/child/{childId}")
    fun getEventsForChild(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<EventResponse>> {
        return ResponseEntity.ok(eventService.getEventsForChild(childId, auth.currentUser()))
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
