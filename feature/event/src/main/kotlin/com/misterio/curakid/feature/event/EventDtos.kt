package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventStatus
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineEvent
import com.misterio.curakid.core.database.event.VaccineType
import jakarta.validation.constraints.NotNull
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle
import java.time.LocalDate

import java.time.LocalDateTime
import java.util.UUID

data class EventRequest(
    val title: String,
    val type: EventType,
    val date: LocalDateTime,
    val location: String? = null,
    val description: String? = null,
    val status: EventStatus = EventStatus.PLANNED,
    val childId: UUID
)

data class UpdateEventRequest(
    val title: String? = null,
    val type: EventType? = null,
    val date: LocalDateTime? = null,
    val location: String? = null,
    val description: String? = null,
    val status: EventStatus? = null
)

data class EventResponse(
    val childId: UUID,
    val id: UUID,
    val type: EventType,
    val date: LocalDateTime,

    val title: String,
    val location: String?,
    val description: String?,
    val status: EventStatus
)

data class VaccineEventResponse(
    val childId: UUID,
    val id: UUID,
    val type: EventType,
    val date: LocalDateTime,
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
    childId = child?.id!!,
    id = id!!,
    type = type,
    date = date,
    title = title,
    location = location,
    description = description,
    status = status
)

fun VaccineEvent.toResponse() = VaccineEventResponse(
    childId = child?.id!!,
    id = id!!,
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
