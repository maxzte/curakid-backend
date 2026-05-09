package com.misterio.curakid.auth

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TokenBlacklistService(
    private val tokenBlacklistRepository: TokenBlacklistRepository
) {
    fun addToBlacklist(jti: String, expiryDate: Instant) {
        tokenBlacklistRepository.save(TokenBlacklist(jti, expiryDate))
    }

    fun isBlacklisted(jti: String): Boolean {
        return tokenBlacklistRepository.existsByJti(jti)
    }

    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    @Transactional
    fun clearExpiredTokens() {
        tokenBlacklistRepository.deleteAllByExpiryDateBefore(Instant.now())
    }
}
