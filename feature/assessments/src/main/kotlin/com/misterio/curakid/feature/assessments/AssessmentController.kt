package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.core.essential.common.exceptions.OnboardingRequiredException
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.NoSuchElementException
import java.util.UUID

@RestController
@RequestMapping("/api/children/{childId}/assessments")
class AssessmentController(
    private val assessmentService: SkillAssessmentService,
    private val childRepository: ChildRepository
) {
    @GetMapping
    fun getAssessments(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<AssessmentResponse>> {
        val child = findChildOrThrow(childId, auth.currentUser())
        return ResponseEntity.ok(assessmentService.getAssessmentsByChild(child))
    }

    @PostMapping("/{periodId}")
    fun saveAnswers(
        @PathVariable childId: UUID,
        @PathVariable periodId: UUID,
        @Valid @RequestBody request: SaveAnswersRequest,
        auth: Authentication
    ): ResponseEntity<SaveAnswersResponse> {
        val child = findChildOrThrow(childId, auth.currentUser())
        return ResponseEntity.status(201).body(assessmentService.saveAssessmentAnswers(child, periodId, request))
    }

    @PatchMapping("/{assessmentId}")
    fun updateAnswers(
        @PathVariable childId: UUID,
        @PathVariable assessmentId: UUID,
        @Valid @RequestBody request: AnswerRequest,
        auth: Authentication
    ): ResponseEntity<AssessmentResponse> {
        val child = findChildOrThrow(childId, auth.currentUser())
        return ResponseEntity.ok(assessmentService.updateAssessmentAnswers(assessmentId, child, request))
    }

    @PostMapping("/complete")
    fun completePeriodAssessment(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: CompletePeriodAssessmentRequest,
        auth: Authentication
    ): ResponseEntity<CompletePeriodAssessmentResponse> {
        val child = findChildOrThrow(childId, auth.currentUser())
        return ResponseEntity.accepted().body(assessmentService.completePeriodAssessment(child, request))
    }

    @GetMapping("/readiness")
    fun getReadiness(
        @PathVariable childId: UUID,
        @RequestParam periodCode: String,
        auth: Authentication
    ): ResponseEntity<PeriodReadinessResponse> {
        val child = findChildOrThrow(childId, auth.currentUser())
        return ResponseEntity.ok(assessmentService.getPeriodReadiness(child, periodCode))
    }

    private fun findChildOrThrow(childId: UUID, user: User): Child {
        return childRepository.findByIdAndUser(childId, user).orElseGet {
            if (childRepository.findAllByUser(user).isEmpty()) {
                throw OnboardingRequiredException()
            }
            throw NoSuchElementException("Child not found")
        }
    }
}
