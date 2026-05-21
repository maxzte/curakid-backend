package com.misterio.curakid.feature.children

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.common.events.ChildCreatedEvent
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ChildService(
    private val userRepository: UserRepository,
    private val childRepository: ChildRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    fun getChildren(user: User): List<ChildResponse> {
        return childRepository.findAllByUser(user).map { ChildResponse.from(it) }
    }

    fun getChild(childId: UUID, user: User): ChildResponse {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        return ChildResponse.from(child)
    }

    @Transactional
    fun createChild(user: User, request: CreateChildRequest): ChildResponse {
        val managedUser = userRepository.findById(user.id!!)
            .orElseThrow { NoSuchElementException("User not found") }
        managedUser.onboardingCompleted = true
        val child = Child(
            user = user,
            name = request.name,
            birthDate = request.birthDate,
            gender = request.gender,
            isPremature = request.isPremature,
            allergies = request.allergies ?: emptyList(),
            chronicConditions = request.chronicDiseases ?: emptyList(),
            pastIllnesses = request.pastDiseases ?: emptyList(),
            notes = request.specialFeatures ?: emptyList(),
        )
        val saved = childRepository.save(child)
        eventPublisher.publishEvent(ChildCreatedEvent(saved.id!!))
        return ChildResponse.from(saved)
    }

    @Transactional
    fun updateChild(childId: UUID, user: User, request: UpdateChildRequest): ChildResponse {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }

        request.name?.let { child.name = it }
        request.birthDate?.let { child.birthDate = it }
        request.gender?.let { child.gender = it }
        request.allergies?.let { child.allergies = it }
        request.chronicDiseases?.let { child.chronicConditions = it }
        request.pastDiseases?.let { child.pastIllnesses = it }
        request.specialFeatures?.let { child.notes = it }

        return ChildResponse.from(childRepository.save(child))
    }

    @Transactional
    fun deleteChild(childId: UUID, user: User) {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        childRepository.delete(child)
    }
}
