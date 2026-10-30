package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.auth.User
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class LegalDocumentResponse(
    val type: String,
    val version: String,
    val url: String,
    val publishedAt: Instant,
)

data class RegisterRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    val password: String,

    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name must not exceed 100 characters")
    val name: String,

    @field:Valid
    @field:NotNull(message = "Consent is required")
    val consent: ConsentRequest,
)

data class ConsentRequest(
    val termsAccepted: Boolean,        // true
    val privacyAccepted: Boolean,      // true
    val consentTimestamp: String,      // ISO 8601
    val consentVersion: String,        // "1.0.2"
    val appVersion: String,            // "1.2.0"
    val deviceLocale: String           // "uk-UA"
)

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val onboardingCompleted: Boolean,
)

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String
)

data class OAuthRequest(
    @field:NotBlank(message = "ID token is required")
    val idToken: String
)

data class VerifyEmailRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Code is required")
    @field:Size(min = 6, max = 6, message = "Code must be exactly 6 characters")
    val code: String
)

data class ForgotPasswordRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Invalid email format")
    val email: String,

    @field:NotBlank(message = "Code is required")
    @field:Size(min = 6, max = 6, message = "Code must be exactly 6 characters")
    val code: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    val newPassword: String
)

data class MessageResponse(
    val message: String
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val role: String,
    val isActive: Boolean,
    val onboardingCompleted: Boolean
) {
    companion object {
        fun from(user: User): UserResponse = UserResponse(
            id = requireNotNull(user.id) { "User ID must not be null" },
            email = user.email,
            name = user.name,
            role = user.role.name,
            isActive = user.isActive,
            onboardingCompleted = user.onboardingCompleted
        )
    }
}

data class UpdateUserRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name must not exceed 100 characters")
    val name: String
)
