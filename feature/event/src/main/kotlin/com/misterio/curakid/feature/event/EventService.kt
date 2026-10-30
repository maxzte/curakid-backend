package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineEventRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import com.misterio.curakid.core.essential.common.PagedResponse
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val childRepository: ChildRepository,
    private val vaccineEventRepository: VaccineEventRepository,
    private val transactionManager: PlatformTransactionManager
) {

    fun getEventsForChild(childId: UUID, user: User): List<EventResponse> {
        val child = requireNotNull(transactionManager.readTx {
            childRepository.findByIdAndUser(childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
        })
        return eventRepository.findAllByChildAndTypeNot(child, EventType.VACCINE)
            .map { it.toResponse() }
    }

    fun getEventsForChild(
        childId: UUID,
        user: User,
        startDate: Instant?,
        endDate: Instant?,
        pageable: Pageable
    ): PagedResponse<EventResponse> {
        val child = requireNotNull(transactionManager.readTx {
            childRepository.findByIdAndUser(childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
        })
        val page = eventRepository.findAllByChildFiltered(child, startDate, endDate, pageable)
            .map { it.toResponse() }
        return PagedResponse(
            content = page.content,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            last = page.isLast
        )
    }

    fun getVaccineEventsFor(childId: UUID, user: User): List<VaccineEventResponse> {
        val child = requireNotNull(transactionManager.readTx {
            childRepository.findByIdAndUser(childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
        })
        return vaccineEventRepository.findAllByChild(child).map { it.toResponse() }
    }

    fun getEvent(childId: UUID, eventId: UUID, user: User): EventResponse {
        val event = requireNotNull(transactionManager.readTx {
            eventRepository.findByIdAndChildUser(eventId, user)
                .orElseThrow { NoSuchElementException("Event not found") }
        })
        if (event.child?.id != childId) {
            throw NoSuchElementException("Event not found")
        }
        return event.toResponse()
    }

    fun createEvent(user: User, request: EventRequest): EventResponse {
        // Phase 1: Read Context
        val (child, existing) = requireNotNull(transactionManager.readTx {
            val c = childRepository.findByIdAndUser(request.childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
            val ex = eventRepository.findById(request.id).orElse(null)
            Pair(c, ex)
        })

        // Phase 2: Domain Processing
        if (existing != null) {
            if (existing.child?.user?.id != user.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Event with this ID already exists")
            }
            return existing.toResponse()
        }

        val event = Event(
            title = request.title,
            type = request.type,
            date = request.date,
            location = request.location,
            description = request.description,
            child = child,
            status = request.status,
        ).apply { id = request.id }

        // Phase 3: Write Transaction
        val saved = requireNotNull(transactionManager.writeTx {
            eventRepository.save(event)
        })
        return saved.toResponse()
    }

    fun createEvents(user: User, requests: List<EventRequest>): List<EventResponse> {
        if (requests.isEmpty()) return emptyList()

        // Phase 1: Read Context
        val (childCache, existingMap) = requireNotNull(transactionManager.readTx {
            val childIds = requests.map { it.childId }.toSet()
            val children = childIds.associateWith { id ->
                childRepository.findByIdAndUser(id, user)
                    .orElseThrow { NoSuchElementException("Child not found") }
            }
            val requestIds = requests.map { it.id }.toSet()
            val existingEvents = eventRepository.findAllById(requestIds).associateBy { it.id }
            Pair(children, existingEvents)
        })

        // Phase 2: Domain Processing (Pure in-memory mapping)
        val results = mutableListOf<EventResponse>()
        val toCreate = mutableListOf<Event>()

        requests.forEach { request ->
            val eventChild = childCache[request.childId]
                ?: throw NoSuchElementException("Child not found")
            val existing = existingMap[request.id]

            if (existing != null) {
                if (existing.child?.user?.id != user.id) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Event with ID ${request.id} already exists")
                }
                results.add(existing.toResponse())
            } else {
                val event = Event(
                    title = request.title,
                    type = request.type,
                    date = request.date,
                    location = request.location,
                    description = request.description,
                    child = eventChild,
                    status = request.status,
                ).apply { id = request.id }
                toCreate.add(event)
            }
        }

        // Phase 3: Write Transaction
        if (toCreate.isNotEmpty()) {
            val savedList = requireNotNull(transactionManager.writeTx {
                eventRepository.saveAll(toCreate)
            })
            results.addAll(savedList.map { it.toResponse() })
        }

        return results
    }

    fun updateEvent(childId: UUID, eventId: UUID, user: User, request: UpdateEventRequest): EventResponse {
        // Phase 1: Read
        val event = requireNotNull(transactionManager.readTx {
            eventRepository.findByIdAndChildUser(eventId, user)
                .orElseThrow { NoSuchElementException("Event not found") }
        })

        if (event.child?.id != childId) {
            throw NoSuchElementException("Event not found")
        }

        // Phase 2: Domain Processing
        request.title?.let { event.title = it }
        request.type?.let { event.type = it }
        request.date?.let { event.date = it }
        request.location?.let { event.location = it }
        request.description?.let { event.description = it }
        request.status?.let { event.status = it }

        // Phase 3: Write
        val saved = requireNotNull(transactionManager.writeTx {
            eventRepository.save(event)
        })
        return saved.toResponse()
    }

    fun deleteEvent(childId: UUID, eventId: UUID, user: User) {
        // Phase 1: Read
        val event = requireNotNull(transactionManager.readTx {
            eventRepository.findByIdAndChildUser(eventId, user)
                .orElseThrow { NoSuchElementException("Event not found") }
        })

        if (event.child?.id != childId) {
            throw NoSuchElementException("Event not found")
        }

        // Phase 2: Domain Processing
        event.deletedAt = Instant.now()

        // Phase 3: Write
        transactionManager.writeTx {
            eventRepository.save(event)
        }
    }
}
