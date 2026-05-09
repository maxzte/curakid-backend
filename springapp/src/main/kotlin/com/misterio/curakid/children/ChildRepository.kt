package com.misterio.curakid.children

import com.misterio.curakid.auth.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface ChildRepository : JpaRepository<Child, UUID> {
    fun findAllByUser(user: User): List<Child>
    fun findByIdAndUser(id: UUID, user: User): Optional<Child>
    fun existsByIdAndUser(id: UUID, user: User): Boolean
}
