package com.misterio.curakid.core.security

import com.google.common.cache.CacheBuilder
import com.misterio.curakid.core.database.token.TokenBlacklist
import com.misterio.curakid.core.database.token.TokenBlacklistRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.concurrent.TimeUnit

@Service
class TokenBlacklistService(
    private val tokenBlacklistRepository: TokenBlacklistRepository
) {
    private val blacklistCache = CacheBuilder.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, Boolean>()

    fun addToBlacklist(jti: String, expiryDate: Instant) {
        blacklistCache.put(jti, true)
        tokenBlacklistRepository.save(TokenBlacklist(jti, expiryDate))
    }

    fun isBlacklisted(jti: String): Boolean {
        return blacklistCache.get(jti) {
            tokenBlacklistRepository.existsByJti(jti)
        }
    }

    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    @Transactional
    fun clearExpiredTokens() {
        blacklistCache.invalidateAll()
        tokenBlacklistRepository.deleteAllByExpiryDateBefore(Instant.now())
    }
}