package com.misterio.curakid.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {
    fun findByToken(token: String): Optional<RefreshToken>

    @Modifying
    @Transactional
    fun deleteByUser(user: User): Int
}

interface TokenBlacklistRepository : JpaRepository<TokenBlacklist, String> {
    fun existsByJti(jti: String): Boolean
    fun deleteAllByExpiryDateBefore(now: java.time.Instant)
}

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByToken(token: String): Optional<PasswordResetToken>
}

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, UUID> {
    fun findByToken(token: String): Optional<EmailVerificationToken>
}

interface OAuthProviderRepository : JpaRepository<OAuthProvider, UUID> {
    fun findByProviderNameAndProviderUserId(providerName: String, providerUserId: String): Optional<OAuthProvider>
}
