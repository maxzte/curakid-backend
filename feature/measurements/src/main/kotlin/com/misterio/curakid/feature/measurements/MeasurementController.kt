package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.core.database.measurements.MeasurementWithReportProjection
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/children/{childId}/measurements")
class MeasurementController(
    private val measurementService: MeasurementService
) {
    @GetMapping
    fun getMeasurements(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<List<MeasurementWithReportProjection>> {
        return ResponseEntity.ok(measurementService.getMeasurements(childId, auth.currentUser()))
    }

    @PostMapping
    fun addMeasurement(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: AddMeasurementRequest,
        auth: Authentication
    ): ResponseEntity<MeasurementResponse> {
        return ResponseEntity.status(201).body(measurementService.addMeasurement(childId, auth.currentUser(), request))
    }
}
