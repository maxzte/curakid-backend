package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.token.RefreshToken
import com.misterio.curakid.core.database.token.RefreshTokenRepository
import com.misterio.curakid.core.security.JwtService
import com.misterio.curakid.core.security.TokenBlacklistService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager,
    private val tokenBlacklistService: TokenBlacklistService,
    private val emailService: EmailService,
    @Value("\${jwt.refresh-token-expiration:604800000}") private val refreshExpiration: Long, // 7 days
) {
    @Transactional
    fun register(request: RegisterRequest): MessageResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw RuntimeException("Email already exists")
        }

        val verificationCode = (100000..999999).random().toString()
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)
                ?: throw IllegalStateException("Password encoding failed"),
            name = request.name,
            isActive = false
        ).apply {
            this.verificationCode = verificationCode
            this.verificationCodeExpiry = Instant.now().plus(java.time.Duration.ofMinutes(15))
        }
        userRepository.save(user)

        emailService.sendVerificationEmail(user.email, user.name, verificationCode)

        return MessageResponse("Registration successful. Please check your email for verification code.")
    }

    @Transactional
    fun verifyEmail(request: VerifyEmailRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("User not found") }

        if (user.isActive) {
            throw RuntimeException("Email already verified")
        }

        if (user.verificationCode != request.code || user.verificationCodeExpiry?.isBefore(Instant.now()) == true) {
            throw RuntimeException("Invalid or expired verification code")
        }

        user.isActive = true
        user.verificationCode = null
        user.verificationCodeExpiry = null
        userRepository.save(user)

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, refreshToken, onboardingCompleted = false)
    }

    @Transactional
    fun forgotPassword(request: ForgotPasswordRequest): MessageResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("User not found") }

        val resetCode = (100000..999999).random().toString()
        user.resetCode = resetCode
        user.resetCodeExpiry = Instant.now().plus(java.time.Duration.ofMinutes(15))
        userRepository.save(user)

        emailService.sendPasswordResetEmail(user.email, user.name, resetCode)

        return MessageResponse("Password reset code sent to your email.")
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest): MessageResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("User not found") }

        if (user.resetCode != request.code || user.resetCodeExpiry?.isBefore(Instant.now()) == true) {
            throw RuntimeException("Invalid or expired reset code")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
            ?: throw IllegalStateException("Password encoding failed")
        user.resetCode = null
        user.resetCodeExpiry = null
        userRepository.save(user)

        return MessageResponse("Password reset successful. You can now login with your new password.")
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("User not found") }

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, refreshToken, onboardingCompleted = user.onboardingCompleted)
    }

    @Transactional
    fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            .orElseThrow { RuntimeException("Refresh token not found") }

        if (refreshToken.expiryDate < Instant.now()) {
            refreshTokenRepository.delete(refreshToken)
            throw RuntimeException("Refresh token expired")
        }

        val user = refreshToken.user
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Token has no user")
        val accessToken = jwtService.generateAccessToken(user)
        val newRefreshToken = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, newRefreshToken, onboardingCompleted = user.onboardingCompleted)
    }

    @Transactional
    fun logout(authHeader: String?, refreshToken: String) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                val jwt = authHeader.substring(7)
                val jti = jwtService.extractJti(jwt)
                val expiry = jwtService.extractClaim(jwt) { it.expiration }.toInstant()

                if (jti != null) {
                    tokenBlacklistService.addToBlacklist(jti, expiry)
                }
            } catch (_: Exception) {
                // Ignore invalid/expired access token and still revoke refresh token.
            }
        }

        refreshTokenRepository.findByToken(refreshToken).ifPresent {
            refreshTokenRepository.delete(it)
        }
    }

    private fun generateAndSaveRefreshToken(user: User): String {
        refreshTokenRepository.deleteByUser(user)
        refreshTokenRepository.flush()
        val tokenStr = jwtService.generateRefreshToken(user)
        // Using fixed 7 days for now to match default property
        val refreshToken = RefreshToken(
            user = user,
            token = tokenStr,
            expiryDate = Instant.now().plusMillis(refreshExpiration)
        )
        refreshTokenRepository.save(refreshToken)
        return tokenStr
    }
}
