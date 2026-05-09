package com.misterio.curakid.measurements

import com.misterio.curakid.children.ChildRepository
import com.misterio.curakid.common.currentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/children/{childId}/measurements")
class MeasurementController(
    private val measurementService: MeasurementService,
    private val childRepository: ChildRepository
) {
    @GetMapping
    fun getMeasurements(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<MeasurementResponse>> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.ok(measurementService.getMeasurements(child))
    }

    @PostMapping
    fun addMeasurement(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: AddMeasurementRequest,
        auth: Authentication
    ): ResponseEntity<MeasurementResponse> {
        val child = childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
        return ResponseEntity.status(201).body(measurementService.addMeasurement(child, request))
    }
}
