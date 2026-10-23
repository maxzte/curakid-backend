package com.misterio.curakid.feature.auth.service

import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserConsent
import com.misterio.curakid.core.database.auth.UserConsentRepository
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
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
    private val childRepository: ChildRepository? = null,
    private val eventRepository: EventRepository? = null,
    private val deviceTokenRepository: DeviceTokenRepository? = null,
    @Value("\${jwt.refresh-token-expiration:604800000}") private val refreshExpiration: Long = 604800000L, // 7 days
) {
    companion object {
        private const val MAX_OTP_ATTEMPTS = 5
    }

    private val secureRandom = java.security.SecureRandom()

    private fun generateSixDigitCode(): String = String.format("%06d", secureRandom.nextInt(1_000_000))

    @Transactional
    fun register(request: RegisterRequest, httpRequest: HttpServletRequest): MessageResponse {
        val existingUser = userRepository.findByEmail(request.email).orElse(null)
        if (existingUser != null) {
            if (existingUser.isActive) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Email already exists")
            }
            if (existingUser.deletedAt != null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Account cannot be re-registered")
            }
            // User registered earlier but never verified email -> re-send fresh verification code
            val newCode = generateSixDigitCode()
            existingUser.verificationCode = newCode
            existingUser.verificationCodeExpiry = Instant.now().plus(Duration.ofMinutes(15))
            existingUser.failedVerificationAttempts = 0
            existingUser.passwordHash = passwordEncoder.encode(request.password)
                ?: throw IllegalStateException("Password encoding failed")
            existingUser.name = request.name
            userRepository.save(existingUser)
            emailService.sendVerificationEmail(existingUser.email, existingUser.name, newCode)
            return MessageResponse("Registration successful. Please check your email for verification code.")
        }

        require(request.consent.termsAccepted) {
            throw ConsentRequiredException("Необхідно прийняти умови використання")
        }
        require(request.consent.privacyAccepted) {
            throw ConsentRequiredException("Необхідно прийняти політику конфіденційності")
        }

        val timestamp = Instant.now()
        val ip = extractClientIp(httpRequest)
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
            this.failedVerificationAttempts = 0
        }

        val u = userRepository.save(user)
        userConsentRepository.saveAll(listOf(
            UserConsent(
                user = u,
                consentType = ConsentType.TERMS,
                accepted = true,
                consentVersion = request.consent.consentVersion,
                acceptedAt = timestamp,
                ipAddress = ip,
                userAgent = userAgent,
                appVersion = request.consent.appVersion
            ),
            UserConsent(
                user = u,
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

    private fun extractClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",").first().trim()
        }
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp.trim()
        }
        return request.remoteAddr ?: "unknown"
    }

    @Transactional
    fun verifyEmail(request: VerifyEmailRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { NoSuchElementException("User not found") }

        if (user.isActive) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already verified")
        }

        val now = Instant.now()
        val isExpired = user.verificationCodeExpiry?.isBefore(now) == true
        val isCodeMismatch = user.verificationCode != request.code

        if (isCodeMismatch || isExpired) {
            user.failedVerificationAttempts++
            if (user.failedVerificationAttempts >= MAX_OTP_ATTEMPTS) {
                user.verificationCode = null
                user.verificationCodeExpiry = null
                userRepository.save(user)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Забагато невдалих спроб. Код анульовано. Запросіть новий код.")
            }
            userRepository.save(user)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Невірний або прострочений код підтвердження")
        }

        user.isActive = true
        user.verificationCode = null
        user.verificationCodeExpiry = null
        user.failedVerificationAttempts = 0
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
        user.failedResetAttempts = 0
        userRepository.save(user)

        emailService.sendPasswordResetEmail(user.email, user.name, resetCode)

        return MessageResponse("Password reset code sent to your email.")
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest): MessageResponse {
        val user = userRepository.findByEmail(request.email)
            .orElseThrow { NoSuchElementException("User not found") }

        val now = Instant.now()
        val isExpired = user.resetCodeExpiry?.isBefore(now) == true
        val isCodeMismatch = user.resetCode != request.code

        if (isCodeMismatch || isExpired) {
            user.failedResetAttempts++
            if (user.failedResetAttempts >= MAX_OTP_ATTEMPTS) {
                user.resetCode = null
                user.resetCodeExpiry = null
                userRepository.save(user)
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Забагато невдалих спроб. Код відновлення анульовано. Запросіть новий код.")
            }
            userRepository.save(user)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Невірний або прострочений код скидання паролю")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
            ?: throw IllegalStateException("Password encoding failed")
        user.resetCode = null
        user.resetCodeExpiry = null
        user.failedResetAttempts = 0
        userRepository.save(user)

        return MessageResponse("Password reset successful. You can now login with your new password.")
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        val user = userRepository.findByEmail(request.email)
            .orElseThrow { NoSuchElementException("User not found") }

        if (!user.isActive) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Account not verified")
        }

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, refreshToken, onboardingCompleted = user.onboardingCompleted)
    }

    @Transactional
    fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token not found") }

        if (refreshToken.expiryDate < Instant.now()) {
            refreshTokenRepository.delete(refreshToken)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired")
        }

        val tokenType = jwtService.extractTokenType(request.refreshToken)
        if (tokenType != null && tokenType != JwtService.TYPE_REFRESH) {
            refreshTokenRepository.delete(refreshToken)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type for refresh")
        }

        val user = refreshToken.user
            ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Token has no user")

        if (!user.isActive) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "User account is inactive")
        }

        val accessToken = jwtService.generateAccessToken(user)
        refreshTokenRepository.delete(refreshToken)
        refreshTokenRepository.flush()
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
            NoSuchElementException("User not found")
        }

        val now = Instant.now()
        
        // Revoke all refresh tokens
        refreshTokenRepository.deleteByUser(user)
        refreshTokenRepository.flush()

        // Deactivate all push notification tokens
        deviceTokenRepository?.deleteAllByUserId(userId)

        // Soft-delete children and their associated events
        val children = childRepository?.findAllByUser(user) ?: emptyList()
        for (child in children) {
            val events = eventRepository?.findAllByChild(child) ?: emptyList()
            events.forEach { event ->
                event.deletedAt = now
            }
            if (events.isNotEmpty()) {
                eventRepository?.saveAll(events)
            }
            child.deletedAt = now
            child.notes = emptyList() // Clear free-text medical notes for privacy
        }
        if (children.isNotEmpty()) {
            childRepository?.saveAll(children)
        }
        
        // Anonymize user data to free up email and comply with GDPR
        user.email = "deleted_${user.id}_${now.epochSecond}@deleted.curakid.com"
        user.name = "Deleted User"
        user.passwordHash = "DELETED"
        user.deletedAt = now
        user.isActive = false
        user.verificationCode = null
        user.resetCode = null

        userRepository.save(user)
        userConsentRepository.revokeAllByUserId(userId, now)
    }

    private fun generateAndSaveRefreshToken(user: User): String {
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
