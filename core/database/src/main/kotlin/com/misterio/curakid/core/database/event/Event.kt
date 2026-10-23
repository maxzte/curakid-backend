package com.misterio.curakid.core.database.event

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import java.util.*

@Entity
@Table(name = "events")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "event_kind", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("EVENT")
@SQLRestriction("deleted_at IS NULL")
open class Event(

    @Column(nullable = false)
    open var title: String = "",

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    open var type: EventType = EventType.USER,

    @Column(nullable = false)
    open var date: Instant = Instant.now(),

    @Column(nullable = true)
    open var location: String? = null,

    @Column(nullable = true)
    open var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    open var child: Child? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    open var status: EventStatus = EventStatus.PLANNED,

    @Column(name = "reminder_sent", nullable = false)
    open var reminderSent: Boolean = false,

) : BaseEntity()


enum class EventType {
    VACCINE,
    DOCTOR,
    USER
}

enum class EventStatus {
    PLANNED,
    DONE,
    SKIPPED,
}
