package com.misterio.curakid.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
            throw RuntimeException("Google token verification failed", e)
        } ?: throw RuntimeException("Invalid Google ID Token")

        val payload = idToken.payload
        val email = payload.email
        val fullName = payload["name"] as String? ?: "Google User"
        val googleUserId = payload.subject

        val user = userRepository.findByEmail(email).orElseGet {
            val newUser = User(
                email = email,
                passwordHash = "", // OAuth users don't have a password
                name = fullName,
                isActive = true
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

        return AuthResponse(accessToken, refreshTokenStr)
    }

    private fun generateAndSaveRefreshToken(user: User): String {
        refreshTokenRepository.deleteByUser(user)
        val tokenStr = jwtService.generateRefreshToken(user)
        val refreshToken = RefreshToken(
            user = user,
            token = tokenStr,
            expiryDate = Instant.now().plusMillis(604800000)
        )
        refreshTokenRepository.save(refreshToken)
        return tokenStr
    }
}
