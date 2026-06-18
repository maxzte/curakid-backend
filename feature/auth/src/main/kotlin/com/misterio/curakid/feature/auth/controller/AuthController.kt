package com.misterio.curakid.feature.auth.controller

import com.misterio.curakid.core.database.currentUser
import com.misterio.curakid.feature.auth.AuthResponse
import com.misterio.curakid.feature.auth.ForgotPasswordRequest
import com.misterio.curakid.feature.auth.LoginRequest
import com.misterio.curakid.feature.auth.MessageResponse
import com.misterio.curakid.feature.auth.OAuthRequest
import com.misterio.curakid.feature.auth.RefreshTokenRequest
import com.misterio.curakid.feature.auth.RegisterRequest
import com.misterio.curakid.feature.auth.ResetPasswordRequest
import com.misterio.curakid.feature.auth.VerifyEmailRequest
import com.misterio.curakid.feature.auth.service.AuthService
import com.misterio.curakid.feature.auth.service.OAuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val oauthService: OAuthService
) {
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<MessageResponse> {
        return ResponseEntity.ok(authService.register(request, httpRequest))
    }

    @PostMapping("/verify-email")
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(authService.verifyEmail(request))
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest): ResponseEntity<MessageResponse> {
        return ResponseEntity.ok(authService.forgotPassword(request))
    }

    @PostMapping("/reset-password")
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest): ResponseEntity<MessageResponse> {
        return ResponseEntity.ok(authService.resetPassword(request))
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
        @RequestBody refreshToken: RefreshTokenRequest
    ): ResponseEntity<Void> {
        authService.logout(authHeader, refreshToken.refreshToken)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/oauth/google")
    fun googleLogin(@RequestBody request: OAuthRequest): ResponseEntity<AuthResponse> {
        return ResponseEntity.ok(oauthService.loginWithGoogle(request.idToken))
    }

    @DeleteMapping("/delete/me")
    fun deleteUser(auth: Authentication): ResponseEntity<Void> {
        authService.deleteMe(auth.currentUser().id ?: return ResponseEntity.noContent().build())
        return ResponseEntity.noContent().build()
    }
}