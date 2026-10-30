package com.misterio.curakid.core.database.event

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

interface EventRepository : JpaRepository<Event, UUID> {

    fun findAllByChild(child: Child): List<Event>

    fun findByIdAndChild(id: UUID, child: Child): Optional<Event>

    fun findByIdAndChildUserId(id: UUID, userId: UUID): Optional<Event>

    fun findAllByChildUserId(userId: UUID): List<Event>

    fun findByIdAndChildUser(id: UUID, user: User): Optional<Event> = findByIdAndChildUserId(id, user.id)

    fun findAllByChildUser(user: User): List<Event> = findAllByChildUserId(user.id)

    fun findAllByChildAndType(child: Child, type: EventType): List<Event>

    fun findAllByChildAndTypeNot(child: Child, type: EventType): List<Event>

    @Query("""
        SELECT e FROM Event e
        WHERE e.child = :child
          AND e.type <> com.misterio.curakid.core.database.event.EventType.VACCINE
          AND (:startDate IS null OR e.date >= :startDate)
          AND (:endDate IS null OR e.date <= :endDate)
        ORDER BY e.date DESC
    """)
    fun findAllByChildFiltered(
        @Param("child") child: Child,
        @Param("startDate") startDate: Instant?,
        @Param("endDate") endDate: Instant?,
        pageable: Pageable
    ): Page<Event>

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

    @Query("""
        SELECT e FROM Event e
        JOIN FETCH e.child c
        JOIN FETCH c.user u
        WHERE e.status = com.misterio.curakid.core.database.event.EventStatus.PLANNED
          AND e.reminderSent = false
          AND e.date BETWEEN :from AND :to
        ORDER BY e.date ASC
    """)
    fun findPendingReminders(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        pageable: Pageable
    ): List<Event>

    @Modifying
    @Transactional
    @Query("UPDATE Event e SET e.reminderSent = true WHERE e.id IN :ids")
    fun markRemindersSent(@Param("ids") ids: Collection<UUID>): Int

}
