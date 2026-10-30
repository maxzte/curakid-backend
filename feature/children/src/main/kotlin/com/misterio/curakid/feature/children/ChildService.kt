package com.misterio.curakid.feature.children

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.essential.common.events.ChildCreatedEvent
import com.misterio.curakid.core.essential.domain.toChildId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class ChildService(
    private val userRepository: UserRepository,
    private val childRepository: ChildRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val eventRepository: EventRepository? = null,
    private val transactionManager: PlatformTransactionManager
) {
    fun getChildren(user: User): List<ChildResponse> {
        return childRepository.findAllByUserIdAndIsActiveTrue(user.id).map { ChildResponse.from(it) }
    }

    fun hasChildren(user: User): Boolean {
        return childRepository.findAllByUserIdAndIsActiveTrue(user.id).isNotEmpty()
    }

    fun getChild(childId: UUID, user: User): ChildResponse {
        val child = requireNotNull(transactionManager.readTx {
            childRepository.findByIdAndUserId(childId, user.id)
                .orElseThrow { NoSuchElementException("Child not found") }
        })
        return ChildResponse.from(child)
    }

    fun createChild(user: User, request: CreateChildRequest): ChildResponse {
        // Phase 1: Read Context
        val (managedUser, existingChild) = requireNotNull(transactionManager.readTx {
            val u = userRepository.findById(user.id)
                .orElseThrow { NoSuchElementException("User not found") }
            val existing = childRepository.findById(request.id).orElse(null)
            Pair(u, existing)
        })

        // Phase 2: Domain Logic (Pure Kotlin, DB connection is free)
        managedUser.onboardingCompleted = true

        if (existingChild != null) {
            if (existingChild.user?.id != user.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Child with this ID already exists")
            }
            return ChildResponse.from(existingChild)
        }

        val child = Child(
            user = managedUser,
            name = request.name,
            birthDate = request.birthDate,
            gender = request.gender,
            isPremature = request.isPremature,
            gestationalWeeks = request.gestationalWeeks,
            allergies = request.allergies ?: emptyList(),
            chronicConditions = request.chronicDiseases ?: emptyList(),
            pastIllnesses = request.pastDiseases ?: emptyList(),
            notes = request.specialFeatures ?: emptyList(),
        ).apply { setId(request.id) }

        // Phase 3: Write & Publish Event
        val saved = requireNotNull(transactionManager.writeTx {
            userRepository.save(managedUser)
            val savedChild = childRepository.save(child)
            val savedId = requireNotNull(savedChild.id) { "Saved Child ID must not be null" }.toChildId()
            eventPublisher.publishEvent(ChildCreatedEvent.from(savedId))
            savedChild
        })

        return ChildResponse.from(saved)
    }

    fun updateChild(childId: UUID, user: User, request: UpdateChildRequest): ChildResponse {
        // Phase 1: Read
        val child = requireNotNull(transactionManager.readTx {
            val c = childRepository.findByIdAndUserId(childId, user.id)
                .orElseThrow { NoSuchElementException("Child not found") }
            c.allergies.size
            c.chronicConditions.size
            c.pastIllnesses.size
            c.notes.size
            c
        })

        // Phase 2: Domain Processing
        request.name?.let { child.name = it }
        request.birthDate?.let { child.birthDate = it }
        request.gender?.let { child.gender = it }
        request.gestationalWeeks?.let { child.gestationalWeeks = it }
        request.allergies?.let { child.allergies = it }
        request.chronicDiseases?.let { child.chronicConditions = it }
        request.pastDiseases?.let { child.pastIllnesses = it }
        request.specialFeatures?.let { child.notes = it }

        // Phase 3: Write
        val saved = requireNotNull(transactionManager.writeTx {
            childRepository.save(child)
        })

        return ChildResponse.from(saved)
    }

    fun deleteChild(childId: UUID, user: User) {
        // Phase 1: Read
        val (child, events) = requireNotNull(transactionManager.readTx {
            val c = childRepository.findByIdAndUserId(childId, user.id)
                .orElseThrow { NoSuchElementException("Child not found") }
            c.allergies.size
            c.notes.size
            val ev = eventRepository?.findAllByChild(c) ?: emptyList()
            Pair(c, ev)
        })

        // Phase 2: Domain Processing
        val now = Instant.now()
        child.isActive = false
        child.deletedAt = now
        child.name = "Deleted Child"
        child.notes = emptyList()
        events.forEach { e -> e.deletedAt = now }

        // Phase 3: Write
        transactionManager.writeTx {
            childRepository.save(child)
            if (events.isNotEmpty()) {
                eventRepository?.saveAll(events)
            }
        }
    }
}
