package com.misterio.curakid.feature.auth.service

import com.misterio.curakid.core.database.token.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
class TokenCleanupScheduler(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val redisTemplate: StringRedisTemplate? = null
) {
    private val log = LoggerFactory.getLogger(TokenCleanupScheduler::class.java)

    companion object {
        private const val LOCK_KEY = "lock:scheduler:refresh-token-cleanup"
        private val LOCK_DURATION = Duration.ofMinutes(10)
    }

    // Run every day at 3:00 AM UTC
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupExpiredTokens() {
        if (redisTemplate != null) {
            val acquired = try {
                redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_DURATION)
            } catch (e: Exception) {
                log.warn("Redis unavailable for refresh token cleanup lock: {}", e.message)
                true
            }
            if (acquired != true) {
                log.debug("Another cluster node is running refresh token cleanup. Skipping.")
                return
            }
        }

        try {
            val deleted = refreshTokenRepository.deleteAllByExpiryDateBefore(Instant.now())
            if (deleted > 0) {
                log.info("Expired refresh tokens cleaned up: count={}", deleted)
            }
        } catch (e: Exception) {
            log.error("Failed to clean up expired refresh tokens: {}", e.message, e)
        }
    }
}
