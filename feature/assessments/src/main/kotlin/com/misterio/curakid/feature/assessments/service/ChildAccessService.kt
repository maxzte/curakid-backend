package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.essential.common.exceptions.OnboardingRequiredException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Resolves a child for the current user and enforces ownership rules.
 */
@Component
class ChildAccessService(
    private val childRepository: ChildRepository? = null
) {

    fun findChildOrThrow(childId: UUID, user: User): Child {
        val repo = childRepository ?: throw IllegalStateException("ChildRepository not configured")
        return repo.findByIdAndUser(childId, user).orElseGet {
            if (repo.findAllByUser(user).isEmpty()) {
                throw OnboardingRequiredException()
            }
            throw NoSuchElementException("Child not found")
        }
    }
}
