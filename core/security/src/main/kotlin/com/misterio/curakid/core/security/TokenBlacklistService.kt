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
        private const val LOCK_KEY_CLEANUP = "lock:scheduler:blacklist-cleanup"
        private val LOCK_DURATION = Duration.ofMinutes(10)
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
        // Fast L1 in-memory check
        val inLocalCache = localFallbackCache.getIfPresent(jti)
        if (inLocalCache != null) {
            return inLocalCache
        }

        // Redis is the authoritative source for blacklisted tokens
        if (redisTemplate != null) {
            try {
                val existsInRedis = redisTemplate.hasKey(REDIS_KEY_PREFIX + jti) == true
                if (existsInRedis) {
                    localFallbackCache.put(jti, true)
                    return true
                }
                // Key does not exist in Redis -> token is NOT blacklisted.
                // Do NOT fall through to PostgreSQL!
                return false
            } catch (e: Exception) {
                log.warn("Redis error checking blacklist for jti {}: {}, falling back to database", jti, e.message)
            }
        }

        // Only reach database if Redis is unavailable or unconfigured
        val inDb = tokenBlacklistRepository.existsByJti(jti)
        localFallbackCache.put(jti, inDb)
        return inDb
    }

    @Scheduled(cron = "0 0 3 * * *") // Every day at 3 AM
    @Transactional
    fun clearExpiredTokens() {
        if (redisTemplate != null) {
            val acquired = try {
                redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_CLEANUP, "1", LOCK_DURATION)
            } catch (e: Exception) {
                log.warn("Redis unavailable for blacklist cleanup scheduler lock: {}", e.message)
                true
            }
            if (acquired != true) {
                log.debug("Another cluster node is running blacklist cleanup scheduler. Skipping.")
                return
            }
        }

        localFallbackCache.invalidateAll()
        tokenBlacklistRepository.deleteAllByExpiryDateBefore(Instant.now())
        log.info("Expired token blacklist entries cleaned up")
    }
}