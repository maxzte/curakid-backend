package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.core.essential.common.PagedResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/children/{childId}/measurements")
class MeasurementController(
    private val measurementService: MeasurementService
) {
    @GetMapping
    fun getMeasurements(
        @PathVariable childId: UUID,
        pageable: Pageable,
        auth: Authentication
    ): ResponseEntity<PagedResponse<MeasurementResponse>> {
        return ResponseEntity.ok(measurementService.getMeasurements(childId, auth.currentUser(), pageable))
    }

    @PostMapping
    fun addMeasurement(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: AddMeasurementRequest,
        auth: Authentication
    ): ResponseEntity<MeasurementResponse> {
        return ResponseEntity.status(201).body(measurementService.addMeasurement(childId, auth.currentUser(), request))
    }

    @PutMapping("/{measurementId}")
    fun updateMeasurement(
        @PathVariable childId: UUID,
        @PathVariable measurementId: UUID,
        @Valid @RequestBody request: AddMeasurementRequest,
        auth: Authentication
    ): ResponseEntity<MeasurementResponse> {
        if (request.id != measurementId) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "measurementId in path ($measurementId) does not match id in request body (${request.id})"
            )
        }
        return ResponseEntity.ok(measurementService.addMeasurement(childId, auth.currentUser(), request))
    }

    @DeleteMapping("/{measurementId}")
    fun deleteMeasurement(
        @PathVariable childId: UUID,
        @PathVariable measurementId: UUID,
        auth: Authentication
    ): ResponseEntity<Void> {
        measurementService.deleteMeasurement(childId, measurementId, auth.currentUser())
        return ResponseEntity.noContent().build()
    }
}
