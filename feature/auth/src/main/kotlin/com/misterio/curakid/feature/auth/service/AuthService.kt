package com.misterio.curakid.feature.auth.service

import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserConsent
import com.misterio.curakid.core.database.auth.UserConsentRepository
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.token.RefreshToken
import com.misterio.curakid.core.database.token.RefreshTokenRepository
import com.misterio.curakid.core.security.JwtService
import com.misterio.curakid.core.security.TokenBlacklistService
import com.misterio.curakid.feature.auth.AuthResponse
import com.misterio.curakid.feature.auth.ConsentRequiredException
import com.misterio.curakid.feature.auth.ForgotPasswordRequest
import com.misterio.curakid.feature.auth.LoginRequest
import com.misterio.curakid.feature.auth.MessageResponse
import com.misterio.curakid.feature.auth.RefreshTokenRequest
import com.misterio.curakid.feature.auth.RegisterRequest
import com.misterio.curakid.feature.auth.ResetPasswordRequest
import com.misterio.curakid.feature.auth.VerifyEmailRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val userConsentRepository: UserConsentRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager,
    private val tokenBlacklistService: TokenBlacklistService,
    private val emailService: EmailService,
    @Value("\${jwt.refresh-token-expiration:604800000}") private val refreshExpiration: Long, // 7 days
) {
    private val secureRandom = java.security.SecureRandom()

    private fun generateSixDigitCode(): String = String.format("%06d", secureRandom.nextInt(1_000_000))

    @Transactional
    fun register(request: RegisterRequest, httpRequest: HttpServletRequest): MessageResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw RuntimeException("Email already exists")
        }

        require(request.consent.termsAccepted) {
            throw ConsentRequiredException("Необхідно прийняти умови використання")
        }
        require(request.consent.privacyAccepted) {
            throw ConsentRequiredException("Необхідно прийняти політику конфіденційності")
        }

        val timestamp = Instant.now()
        val ip = httpRequest.remoteAddr
        val userAgent = httpRequest.getHeader("User-Agent")

        val verificationCode = generateSixDigitCode()
        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password)
                ?: throw IllegalStateException("Password encoding failed"),
            name = request.name,
            isActive = false
        ).apply {
            this.verificationCode = verificationCode
            this.verificationCodeExpiry = Instant.now().plus(Duration.ofMinutes(15))
        }

        val u = userRepository.save(user)
        userConsentRepository.saveAll(listOf(
            UserConsent(
                userId = u.id!!,
                consentType = ConsentType.TERMS,
                accepted = true,
                consentVersion = request.consent.consentVersion,
                acceptedAt = timestamp,
                ipAddress = ip,
                userAgent = userAgent,
                appVersion = request.consent.appVersion
            ),
            UserConsent(
                userId = u.id!!,
                consentType = ConsentType.PRIVACY_POLICY,
                accepted = true,
                consentVersion = request.consent.consentVersion,
                acceptedAt = timestamp,
                ipAddress = ip,
                userAgent = userAgent,
                appVersion = request.consent.appVersion
            )
        ))

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
        val userOptional = userRepository.findByEmail(request.email)
        if (userOptional.isEmpty) {
            return MessageResponse("Password reset code sent to your email.")
        }

        val user = userOptional.get()
        val resetCode = generateSixDigitCode()
        user.resetCode = resetCode
        user.resetCodeExpiry = Instant.now().plus(Duration.ofMinutes(15))
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

    @Transactional
    fun deleteMe(userId: UUID) {
        val user = userRepository.findById(userId).orElseThrow {
            RuntimeException("User not found")
        }
        
        // Anonymize user data to free up email and comply with GDPR
        user.email = "deleted_${user.id}_${Instant.now().epochSecond}@deleted.curakid.com"
        user.name = "Deleted User"
        user.passwordHash = "DELETED"
        user.deletedAt = Instant.now()
        user.isActive = false

        userRepository.save(user)
        userConsentRepository.revokeAllByUserId(userId, Instant.now())
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
