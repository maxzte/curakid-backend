package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.currentUser
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
import java.util.UUID

@RestController
@RequestMapping("/api/children/{childId}/assessments")
class AssessmentController(
    private val assessmentService: SkillAssessmentService
) {
    @GetMapping
    fun getAssessments(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<AssessmentResponse>> {
        return ResponseEntity.ok(assessmentService.getAssessmentsByChild(childId, auth.currentUser()))
    }

    @PostMapping("/{periodId}")
    fun saveAnswers(
        @PathVariable childId: UUID,
        @PathVariable periodId: UUID,
        @Valid @RequestBody request: SaveAnswersRequest,
        auth: Authentication
    ): ResponseEntity<SaveAnswersResponse> {
        return ResponseEntity.status(201).body(assessmentService.saveAssessmentAnswers(childId, periodId, auth.currentUser(), request))
    }

    @PatchMapping("/{assessmentId}")
    fun updateAnswers(
        @PathVariable childId: UUID,
        @PathVariable assessmentId: UUID,
        @Valid @RequestBody request: AnswerRequest,
        auth: Authentication
    ): ResponseEntity<AssessmentResponse> {
        return ResponseEntity.ok(assessmentService.updateAssessmentAnswers(assessmentId, childId, auth.currentUser(), request))
    }

    @PostMapping("/complete")
    fun completePeriodAssessment(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: CompletePeriodAssessmentRequest,
        auth: Authentication
    ): ResponseEntity<CompletePeriodAssessmentResponse> {
        return ResponseEntity.accepted().body(assessmentService.completePeriodAssessment(childId, auth.currentUser(), request))
    }

    @GetMapping("/readiness")
    fun getReadiness(
        @PathVariable childId: UUID,
        @RequestParam periodCode: String,
        auth: Authentication
    ): ResponseEntity<PeriodReadinessResponse> {
        return ResponseEntity.ok(assessmentService.getPeriodReadiness(childId, periodCode, auth.currentUser()))
    }
}
