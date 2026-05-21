package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineEventRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val childRepository: ChildRepository,
    private val vaccineEventRepository: VaccineEventRepository,
) {

    fun getEventsForChild(childId: UUID, user: User): List<EventResponse> {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        return eventRepository.findAllByChildAndTypeNot(child, EventType.VACCINE).map { it.toResponse() }
    }

    fun getVaccineEventsFor(childId: UUID, user: User): List<VaccineEventResponse> {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        return vaccineEventRepository.findAllByChild(child).map { it.toResponse() }
    }

    fun getEvent(eventId: UUID, user: User): EventResponse {
        val event = eventRepository.findByIdAndChildUser(eventId, user)
            .orElseThrow { NoSuchElementException("Event not found") }
        return event.toResponse()
    }

    @Transactional
    fun createEvent(user: User, request: EventRequest): EventResponse {
        val child = childRepository.findByIdAndUser(request.childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }

        val event = Event(
            title = request.title,
            type = request.type,
            date = request.date,
            location = request.location,
            description = request.description,
            child = child,
            status = request.status
        )

        return eventRepository.save(event).toResponse()
    }

    @Transactional
    fun createEvents(user: User, requests: List<EventRequest>): List<EventResponse> {
        val child = childRepository.findByIdAndUser(requests.first().childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }

        val events = requests.map { request ->
            Event(
                title = request.title,
                type = request.type,
                date = request.date,
                location = request.location,
                description = request.description,
                child = child,
                status = request.status
            )
        }
        val saved = eventRepository.saveAll(events)
        return saved.map { it.toResponse() }
    }

    @Transactional
    fun updateEvent(eventId: UUID, user: User, request: UpdateEventRequest): EventResponse {
        val event = eventRepository.findByIdAndChildUser(eventId, user)
            .orElseThrow { NoSuchElementException("Event not found") }

        request.title?.let { event.title = it }
        request.type?.let { event.type = it }
        request.date?.let { event.date = it }
        request.location?.let { event.location = it }
        request.description?.let { event.description = it }
        request.status?.let { event.status = it }

        return eventRepository.save(event).toResponse()
    }

    @Transactional
    fun deleteEvent(eventId: UUID, user: User) {
        val event = eventRepository.findByIdAndChildUser(eventId, user)
            .orElseThrow { NoSuchElementException("Event not found") }
        eventRepository.delete(event)
    }

}