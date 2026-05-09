package com.misterio.curakid.vaccine

import com.misterio.curakid.children.Child
import com.misterio.curakid.children.ChildRepository
import com.misterio.curakid.common.currentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/children/{childId}/vaccines")
class VaccineController(
    private val vaccineService: VaccineService,
    private val childRepository: ChildRepository
) {

    @GetMapping
    fun getVaccines(
        @PathVariable childId: UUID,
    ): ResponseEntity<List<VaccineResponse>> {
        return ResponseEntity.ok(vaccineService.getVaccines(childId))
    }

    @PatchMapping("/{vaccineId}")
    fun updateVaccine(
        @PathVariable childId: UUID,
        @PathVariable vaccineId: UUID,
        @Valid @RequestBody request: UpdateVaccineRequest,
        auth: Authentication
    ): ResponseEntity<VaccineResponse> {
        getChildOrThrow(childId, auth)
        return ResponseEntity.ok(vaccineService.updateVaccineStatus(vaccineId, request, auth.currentUser()))
    }

    private fun getChildOrThrow(childId: UUID, auth: Authentication): Child =
        childRepository.findByIdAndUser(childId, auth.currentUser())
            .orElseThrow { NoSuchElementException("Child not found") }
}