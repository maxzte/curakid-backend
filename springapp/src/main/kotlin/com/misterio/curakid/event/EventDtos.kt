package com.misterio.curakid.event

import java.time.LocalDateTime
import java.util.UUID

data class EventRequest(
    val title: String,
    val type: EventType,
    val date: LocalDateTime,
    val location: String? = null,
    val description: String? = null,
    val childId: UUID
)

data class UpdateEventRequest(
    val title: String? = null,
    val type: EventType? = null,
    val date: LocalDateTime? = null,
    val location: String? = null,
    val description: String? = null
)

data class EventResponse(
    val id: UUID,
    val title: String,
    val type: EventType,
    val date: LocalDateTime,
    val location: String?,
    val description: String?,
    val childId: UUID
) {
    companion object {
        fun from(event: Event): EventResponse {
            return EventResponse(
                id = event.id!!,
                title = event.title,
                type = event.type,
                date = event.date,
                location = event.location,
                description = event.description,
                childId = event.child?.id!!
            )
        }
    }
}
