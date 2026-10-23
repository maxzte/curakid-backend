package com.misterio.curakid.core.database.event

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.*

interface EventRepository : JpaRepository<Event, UUID> {

    fun findAllByChild(child: Child): List<Event>

    fun findByIdAndChild(id: UUID, child: Child): Optional<Event>

    fun findByIdAndChildUser(id: UUID, user: User): Optional<Event>

    fun findAllByChildUser(user: User): List<Event>

    fun findAllByChildAndType(child: Child, type: EventType): List<Event>

    fun findAllByChildAndTypeNot(child: Child, type: EventType): List<Event>

    @Query("""
        SELECT e FROM Event e
        JOIN FETCH e.child c
        JOIN FETCH c.user u
        WHERE e.status = com.misterio.curakid.core.database.event.EventStatus.PLANNED
          AND e.reminderSent = false
          AND e.date BETWEEN :from AND :to
    """)
    fun findPendingReminders(
        @Param("from") from: Instant,
        @Param("to") to: Instant
    ): List<Event>

}
