package com.misterio.curakid.core.security

import com.google.common.cache.CacheBuilder
import com.misterio.curakid.core.database.token.TokenBlacklist
import com.misterio.curakid.core.database.token.TokenBlacklistRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

@Service
class TokenBlacklistService(
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val redisTemplate: StringRedisTemplate? = null
) {
    private val log = LoggerFactory.getLogger(TokenBlacklistService::class.java)

    companion object {
        private const val REDIS_KEY_PREFIX = "blacklist:token:"
    }

    // Local fallback in-memory cache if Redis is temporarily unavailable or in local test without Redis
    private val localFallbackCache = CacheBuilder.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<String, Boolean>()

    fun addToBlacklist(jti: String, expiryDate: Instant) {
        val ttl = Duration.between(Instant.now(), expiryDate)
        if (redisTemplate != null && !ttl.isNegative && !ttl.isZero) {
            try {
                redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + jti, "1", ttl)
            } catch (e: Exception) {
                log.warn("Failed to save blacklisted token to Redis, falling back to local cache: {}", e.message)
                localFallbackCache.put(jti, true)
            }
        } else {
            localFallbackCache.put(jti, true)
        }

        try {
            tokenBlacklistRepository.save(TokenBlacklist(jti, expiryDate))
        } catch (e: Exception) {
            log.error("Failed to persist token to TokenBlacklistRepository: {}", e.message)
        }
    }

    fun isBlacklisted(jti: String): Boolean {
        if (redisTemplate != null) {
            try {
                val existsInRedis = redisTemplate.hasKey(REDIS_KEY_PREFIX + jti)
                if (existsInRedis == true) {
                    return true
                }
            } catch (e: Exception) {
                log.warn("Redis error checking blacklist for jti {}: {}", jti, e.message)
            }
        }

        val inLocalCache = localFallbackCache.getIfPresent(jti)
        if (inLocalCache != null) {
            return inLocalCache
        }

        val inDb = tokenBlacklistRepository.existsByJti(jti)
        if (inDb) {
            localFallbackCache.put(jti, true)
        }
        return inDb
    }

    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    @Transactional
    fun clearExpiredTokens() {
        localFallbackCache.invalidateAll()
        tokenBlacklistRepository.deleteAllByExpiryDateBefore(Instant.now())
    }
}