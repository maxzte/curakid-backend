package com.misterio.curakid.children

import com.misterio.curakid.common.currentUser
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/children")
class ChildController(
    private val childService: ChildService
) {
    @GetMapping
    fun getChildren(auth: Authentication): ResponseEntity<List<ChildResponse>> {
        return ResponseEntity.ok(childService.getChildren(auth.currentUser()))
    }

    @GetMapping("/{childId}")
    fun getChild(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<ChildResponse> {
        return ResponseEntity.ok(childService.getChild(childId, auth.currentUser()))
    }

    @PostMapping
    fun createChild(
        @Valid @RequestBody request: CreateChildRequest,
        auth: Authentication
    ): ResponseEntity<ChildResponse> {
        return ResponseEntity.status(201).body(childService.createChild(auth.currentUser(), request))
    }

    @PutMapping("/{childId}")
    fun updateChild(
        @PathVariable childId: UUID,
        @Valid @RequestBody request: UpdateChildRequest,
        auth: Authentication
    ): ResponseEntity<ChildResponse> {
        return ResponseEntity.ok(childService.updateChild(childId, auth.currentUser(), request))
    }

    @DeleteMapping("/{childId}")
    fun deleteChild(
        @PathVariable childId: UUID,
        auth: Authentication
    ): ResponseEntity<Void> {
        childService.deleteChild(childId, auth.currentUser())
        return ResponseEntity.noContent().build()
    }
}
