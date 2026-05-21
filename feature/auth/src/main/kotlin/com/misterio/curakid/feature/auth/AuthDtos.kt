package com.misterio.curakid.feature.auth

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val onboardingCompleted: Boolean,
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class OAuthRequest(
    val idToken: String
)

data class VerifyEmailRequest(
    val email: String,
    val code: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

data class MessageResponse(
    val message: String
)
