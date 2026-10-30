package com.misterio.curakid.core.database.children

import com.misterio.curakid.core.database.auth.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface ChildRepository : JpaRepository<Child, UUID> {
    fun findAllByUserId(userId: UUID): List<Child>
    fun findAllByUserIdAndIsActiveTrue(userId: UUID): List<Child>
    fun findAllByIsActiveTrue(): List<Child>
    fun findAllByIsActiveTrue(pageable: Pageable): Page<Child>
    fun findByIdAndUserId(id: UUID, userId: UUID): Optional<Child>
    fun existsByIdAndUserId(id: UUID, userId: UUID): Boolean

    fun findAllByUser(user: User): List<Child> = findAllByUserId(user.id)
    fun findAllByUserAndIsActiveTrue(user: User): List<Child> = findAllByUserIdAndIsActiveTrue(user.id)
    fun findByIdAndUser(id: UUID, user: User): Optional<Child> = findByIdAndUserId(id, user.id)
    fun existsByIdAndUser(id: UUID, user: User): Boolean = existsByIdAndUserId(id, user.id)
}
