package com.misterio.curakid.event

import com.misterio.curakid.children.Child
import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "events")
class Event(

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var type: EventType = EventType.USER,

    @Column(nullable = false)
    var date: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = true)
    var location: String? = null,

    @Column(nullable = true)
    var description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

) : BaseEntity()

enum class EventType {
    VACCINE,
    DOCTOR,
    USER
}
