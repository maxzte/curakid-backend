package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventStatus
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineEvent
import com.misterio.curakid.core.database.event.VaccineType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class EventRequest(
    @field:NotNull(message = "ID is required")
    val id: UUID,

    @field:NotNull(message = "Child ID is required")
    val childId: UUID,

    @field:NotBlank(message = "Title is required")
    @field:Size(max = 200, message = "Title must not exceed 200 characters")
    val title: String,

    @field:NotNull(message = "Event type is required")
    val type: EventType,

    @field:NotNull(message = "Date is required")
    val date: Instant,

    @field:Size(max = 255, message = "Location must not exceed 255 characters")
    val location: String? = null,

    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,

    val status: EventStatus = EventStatus.PLANNED,
)

data class UpdateEventRequest(
    @field:Size(max = 200, message = "Title must not exceed 200 characters")
    val title: String? = null,
    val type: EventType? = null,
    val date: Instant? = null,
    @field:Size(max = 255, message = "Location must not exceed 255 characters")
    val location: String? = null,
    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    val description: String? = null,
    val status: EventStatus? = null
)

data class EventResponse(
    val childId: UUID,
    val id: UUID,
    val type: EventType,
    val date: Instant,

    val title: String,
    val location: String?,
    val description: String?,
    val status: EventStatus
)

data class VaccineEventResponse(
    val childId: UUID,
    val id: UUID,
    val type: EventType,
    val date: Instant,
    val status: EventStatus,
    val description: String?,

    val name: String,
    val disease: String?,
    val doseNumber: Int?,
    val totalDoses: Int?,
    val scheduledAgeMonths: Int?,
    val scheduledAgeDays: Int?,
    val vaccineType: VaccineType,
    val notes: String?,
)

fun Event.toResponse() = EventResponse(
    childId = requireNotNull(child?.id) { "Child ID must not be null for Event" },
    id = id,
    type = type,
    date = date,
    title = title,
    location = location,
    description = description,
    status = status
)

fun VaccineEvent.toResponse() = VaccineEventResponse(
    childId = requireNotNull(child?.id) { "Child ID must not be null for VaccineEvent" },
    id = id,
    type = type,
    date = date,
    disease = template?.disease,
    doseNumber = template?.doseNumber ?: 1,
    totalDoses = template?.totalDoses ?: 1,
    scheduledAgeMonths = template?.scheduledAgeMonths,
    scheduledAgeDays = template?.scheduledAgeDays,
    status = status,
    notes = template?.notes,
    name = template?.name ?: "",
    description = description,
    vaccineType = template?.type ?: VaccineType.VACCINATION,
)
