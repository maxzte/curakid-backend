package com.misterio.curakid.assessments

import com.misterio.curakid.children.ChildRepository
import com.misterio.curakid.common.currentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
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
        @RequestParam domain: AssessmentDomain? = null,
        auth: Authentication
    ): ResponseEntity<List<AssessmentResponse>> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.ok(assessmentService.getAssessments(child, domain))
    }

    @PostMapping
    fun saveAssessment(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: SaveAssessmentRequest,
        auth: Authentication
    ): ResponseEntity<AssessmentResponse> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.status(201).body(assessmentService.saveAssessment(child, request))
    }

    @PatchMapping("/{assessmentId}")
    fun updateAssessment(
        @PathVariable childId: UUID,
        @PathVariable assessmentId: UUID,
        @Valid @RequestBody request: SaveAssessmentRequest,
        auth: Authentication
    ): ResponseEntity<AssessmentResponse> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.ok(assessmentService.updateAssessment(child, assessmentId, request))
    }

    @PostMapping("/complete")
    fun completePeriodAssessment(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: CompletePeriodAssessmentRequest,
        auth: Authentication
    ): ResponseEntity<CompletePeriodAssessmentResponse> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.ok(assessmentService.completePeriodAssessment(child, request))
    }
}
