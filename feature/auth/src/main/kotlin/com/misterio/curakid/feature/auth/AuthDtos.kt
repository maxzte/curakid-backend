package com.misterio.curakid.feature.auth

import java.time.Instant

data class LegalDocumentResponse(
    val type: String,
    val version: String,
    val url: String,
    val publishedAt: Instant,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
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
