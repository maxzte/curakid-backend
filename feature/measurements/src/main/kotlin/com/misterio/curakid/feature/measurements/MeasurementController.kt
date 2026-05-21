package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.core.database.measurements.MeasurementWithReportProjection
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

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
    ): ResponseEntity<List<MeasurementWithReportProjection>> {
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
