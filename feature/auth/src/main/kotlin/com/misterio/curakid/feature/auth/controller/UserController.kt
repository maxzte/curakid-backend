package com.misterio.curakid.feature.auth.controller

import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.feature.auth.UpdateUserRequest
import com.misterio.curakid.feature.auth.UserResponse
import com.misterio.curakid.feature.auth.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val authService: AuthService
) {

    @GetMapping("/me")
    fun getCurrentUser(auth: Authentication): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(authService.getUser(auth.currentUser().id))
    }

    @PutMapping("/me")
    fun updateCurrentUser(
        @Valid @RequestBody request: UpdateUserRequest,
        auth: Authentication
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(authService.updateUser(auth.currentUser().id, request))
    }

    @DeleteMapping("/me")
    fun deleteCurrentUser(auth: Authentication): ResponseEntity<Void> {
        authService.deleteMe(auth.currentUser().id)
        return ResponseEntity.noContent().build()
    }
}
