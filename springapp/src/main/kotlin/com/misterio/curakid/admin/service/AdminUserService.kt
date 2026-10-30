package com.misterio.curakid.admin.service

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import com.misterio.curakid.core.database.token.RefreshTokenRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import java.time.Instant
import java.util.*

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val childRepository: ChildRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val eventRepository: EventRepository? = null,
    private val transactionManager: PlatformTransactionManager
) {
    fun getUsers(email: String?, page: Int, size: Int): Page<User> {
        val pageable = PageRequest.of(page, size, Sort.by("email").ascending())
        return requireNotNull(transactionManager.readTx {
            if (email.isNullOrBlank()) {
                userRepository.findAll(pageable)
            } else {
                userRepository.findByEmailContainingIgnoreCase(email, pageable)
            }
        })
    }

    fun deleteUser(userId: UUID) {
        val (user, children) = requireNotNull(transactionManager.readTx {
            val u = userRepository.findById(userId).orElseThrow {
                NoSuchElementException("User not found")
            }
            val c = childRepository.findAllByUser(u)
            c.forEach { child -> child.notes.size }
            Pair(u, c)
        })

        val now = Instant.now()

        transactionManager.writeTx {
            // Revoke active sessions and push tokens
            refreshTokenRepository.deleteByUser(user)
            deviceTokenRepository.deleteAllByUserId(user.id)

            // Anonymize Children and cascade delete events
            children.forEach { child ->
                child.name = "Deleted Child"
                child.deletedAt = now
                child.isActive = false
                child.notes = emptyList()
                childRepository.save(child)

                eventRepository?.let { er ->
                    val events = er.findAllByChild(child)
                    events.forEach { e -> e.deletedAt = now }
                    if (events.isNotEmpty()) er.saveAll(events)
                }
            }

            // Anonymize User
            user.email = "deleted_${user.id}@deleted.com"
            user.name = "Deleted User"
            user.passwordHash = "{noop}deleted" // Invalidate password
            user.isActive = false
            user.deletedAt = now
            user.verificationCode = null
            user.resetCode = null

            userRepository.save(user)
        }
    }
}
