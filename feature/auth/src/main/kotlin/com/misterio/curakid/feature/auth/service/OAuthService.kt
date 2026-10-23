package com.misterio.curakid.feature.auth.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.token.RefreshToken
import com.misterio.curakid.core.database.token.RefreshTokenRepository
import com.misterio.curakid.core.security.JwtService
import com.misterio.curakid.feature.auth.AuthResponse
import com.misterio.curakid.core.database.auth.OAuthProvider
import com.misterio.curakid.core.database.auth.OAuthProviderRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class OAuthService(
    @Value("\${google.client-id:defaultClientId}") private val googleClientId: String,
    private val userRepository: UserRepository,
    private val oauthProviderRepository: OAuthProviderRepository,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository
) {
    private val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory())
        .setAudience(listOf(googleClientId))
        .build()

    @Transactional
    fun loginWithGoogle(idTokenString: String): AuthResponse {

        val idToken = try {
            verifier.verify(idTokenString)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google token verification failed: ${e.message}")
        } ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID Token")

        val payload = idToken.payload
        val email = payload.email
        val fullName = payload["name"] as String? ?: "Google User"
        val googleUserId = payload.subject

        val existingUser = userRepository.findByEmail(email).orElse(null)
        val user = if (existingUser != null) {
            if (existingUser.deletedAt != null) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account was deleted")
            }
            if (!existingUser.isActive) {
                // Verified Google account confirms email ownership -> activate user
                existingUser.isActive = true
                existingUser.verificationCode = null
                existingUser.verificationCodeExpiry = null
                userRepository.save(existingUser)
            } else {
                existingUser
            }
        } else {
            val newUser = User(
                email = email,
                passwordHash = "", // OAuth users don't have a password
                name = fullName,
                isActive = true,
            )
            userRepository.save(newUser)
        }

        oauthProviderRepository.findByProviderNameAndProviderUserId("google", googleUserId)
            .orElseGet {
                val newProvider = OAuthProvider(
                    user = user,
                    providerName = "google",
                    providerUserId = googleUserId
                )
                oauthProviderRepository.save(newProvider)
            }

        val accessToken = jwtService.generateAccessToken(user)
        val refreshTokenStr = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, refreshTokenStr, onboardingCompleted = user.onboardingCompleted)
    }

    private fun generateAndSaveRefreshToken(user: User): String {
        val tokenStr = jwtService.generateRefreshToken(user)
        val refreshToken = RefreshToken(
            user = user,
            token = tokenStr,
            expiryDate = Instant.now().plusMillis(jwtService.refreshExpiration)
        )
        refreshTokenRepository.save(refreshToken)
        return tokenStr
    }
}
