package com.misterio.curakid.event

import com.misterio.curakid.auth.User
import com.misterio.curakid.children.ChildRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val childRepository: ChildRepository
) {

    fun getEventsForChild(childId: UUID, user: User): List<EventResponse> {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        return eventRepository.findAllByChild(child).map { EventResponse.from(it) }
    }

    fun getEvent(eventId: UUID, user: User): EventResponse {
        val event = eventRepository.findByIdAndChildUser(eventId, user)
            .orElseThrow { NoSuchElementException("Event not found") }
        return EventResponse.from(event)
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
            child = child
        )
        
        return EventResponse.from(eventRepository.save(event))
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

        return EventResponse.from(eventRepository.save(event))
    }

    @Transactional
    fun deleteEvent(eventId: UUID, user: User) {
        val event = eventRepository.findByIdAndChildUser(eventId, user)
            .orElseThrow { NoSuchElementException("Event not found") }
        eventRepository.delete(event)
    }
}
