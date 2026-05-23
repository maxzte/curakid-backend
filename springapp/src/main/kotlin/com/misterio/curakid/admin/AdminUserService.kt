package com.misterio.curakid.admin

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.ChildRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdminUserService(
    private val userRepository: UserRepository,
    private val childRepository: ChildRepository
) {
    fun getUsers(email: String?, page: Int, size: Int): Page<User> {
        val pageable = PageRequest.of(page, size, Sort.by("email").ascending())
        return if (email.isNullOrBlank()) {
            userRepository.findAll(pageable)
        } else {
            userRepository.findByEmailContainingIgnoreCase(email, pageable)
        }
    }

    @Transactional
    fun deleteUser(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow {
            NoSuchElementException("User not found")
        }

        // Anonymize Children
        val children = childRepository.findAllByUser(user)
        children.forEach { child ->
            child.name = "Deleted Child"
            childRepository.save(child)
        }

        // Anonymize User
        user.email = "deleted_${user.id}@deleted.com"
        user.name = "Deleted User"
        user.passwordHash = "{noop}deleted" // Invalidate password
        user.isActive = false
        user.verificationCode = null
        user.resetCode = null
        
        userRepository.save(user)
    }
}
