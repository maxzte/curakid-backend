package com.misterio.curakid.auth

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val oauthService: OAuthService
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.register(request))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.login(request))
    }

    @PostMapping("/refresh")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.refreshToken(request))
    }

    @PostMapping("/logout")
    fun logout(
        @RequestHeader("Authorization") authHeader: String?,
        @RequestParam refreshToken: String
    ): ResponseEntity<Void> {
        authService.logout(authHeader, refreshToken)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/oauth/google")
    fun googleLogin(@RequestBody request: OAuthRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(oauthService.loginWithGoogle(request.idToken))
    }
}
