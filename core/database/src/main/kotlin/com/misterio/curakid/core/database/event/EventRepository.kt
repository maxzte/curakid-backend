package com.misterio.curakid.core.database.event

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface EventRepository : JpaRepository<Event, UUID> {

    fun findAllByChild(child: Child): List<Event>

    fun findByIdAndChild(id: UUID, child: Child): Optional<Event>

    fun findByIdAndChildUser(id: UUID, user: User): Optional<Event>

    fun findAllByChildUser(user: User): List<Event>

    fun findAllByChildAndType(child: Child, type: EventType): List<Event>

    fun findAllByChildAndTypeNot(child: Child, type: EventType): List<Event>
}
