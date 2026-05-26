package com.misterio.curakid.core.database.event

import com.misterio.curakid.core.database.children.Child
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "vaccine_events")
class VaccineEvent(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = true)
    var template: VaccineTemplate? = null,

    status: EventStatus = EventStatus.PLANNED,

    child: Child? = null,
    date: Instant = Instant.now(),
    title: String = "",
    description: String = ""

) : Event(
    title = title.ifEmpty { template?.name ?: "" },
    type = EventType.VACCINE,
    date = date,
    child = child,
    status = status,
    description = description
)
