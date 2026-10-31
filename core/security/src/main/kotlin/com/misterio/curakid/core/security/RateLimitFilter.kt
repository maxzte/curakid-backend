package com.misterio.curakid.core.security

import com.google.common.cache.CacheBuilder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
class RateLimitFilter(
    private val redisTemplate: StringRedisTemplate? = null,
    private val jwtService: JwtService? = null
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)

    companion object {
        private const val REDIS_KEY_AUTH_PREFIX = "ratelimit:auth:"
        private const val REDIS_KEY_AI_PREFIX = "ratelimit:ai:"

        // Atomic INCR + PEXPIRE Lua script to guarantee keys always have a TTL even on abrupt restarts
        private val ATOMIC_RATE_LIMIT_SCRIPT = DefaultRedisScript(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """.trimIndent(),
            Long::class.java
        )
    }

    private data class RateWindow(
        val count: AtomicInteger = AtomicInteger(0),
        val windowStart: Long = System.currentTimeMillis()
    )

    // Window: 1 minute. Cleaned automatically by Guava cache as fallback.
    private val fallbackAuthRateLimitCache = CacheBuilder.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build<String, RateWindow>()

    private val fallbackAiRateLimitCache = CacheBuilder.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build<String, RateWindow>()

    // Allow max 15 auth requests per minute per IP to prevent credential stuffing / brute force
    private val maxAuthRequestsPerMinute = 15
    // Allow max 30 heavy AI requests (chat stream / report generation) per minute per User/IP
    private val maxAiRequestsPerMinute = 30

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val isAuth = isRateLimitedAuthEndpoint(path, request.method)
        val isAi = isRateLimitedAiEndpoint(path, request.method)

        if (isAuth || isAi) {
            val identifier = if (isAi) extractAiIdentifier(request) else extractClientIp(request)
            val prefix = if (isAuth) REDIS_KEY_AUTH_PREFIX else REDIS_KEY_AI_PREFIX
            val maxLimit = if (isAuth) maxAuthRequestsPerMinute else maxAiRequestsPerMinute
            val fallbackCache = if (isAuth) fallbackAuthRateLimitCache else fallbackAiRateLimitCache

            val isExceeded = if (redisTemplate != null) {
                checkWithRedis(prefix, identifier, maxLimit, fallbackCache)
            } else {
                checkWithFallback(fallbackCache, identifier, maxLimit)
            }

            if (isExceeded) {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.characterEncoding = "UTF-8"
                response.writer.write(
                    """{"error":"Too Many Requests","message":"Забагато запитів. Будь ласка, зачекайте хвилину."}"""
                )
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun checkWithRedis(
        prefix: String,
        identifier: String,
        maxLimit: Int,
        fallbackCache: com.google.common.cache.Cache<String, RateWindow>
    ): Boolean {
        val key = prefix + identifier
        val redis = redisTemplate ?: return checkWithFallback(fallbackCache, identifier, maxLimit)
        return try {
            val count = redis.execute(
                ATOMIC_RATE_LIMIT_SCRIPT,
                listOf(key),
                "60000" // 60 seconds TTL in milliseconds
            ) ?: 1L
            count > maxLimit
        } catch (e: Exception) {
            log.warn("Redis rate-limiting failed for {}, falling back to local memory: {}", identifier, e.message)
            checkWithFallback(fallbackCache, identifier, maxLimit)
        }
    }

    private fun checkWithFallback(
        fallbackCache: com.google.common.cache.Cache<String, RateWindow>,
        identifier: String,
        maxLimit: Int
    ): Boolean {
        val window = fallbackCache.get(identifier) { RateWindow() }
        val currentCount = window.count.incrementAndGet()
        return currentCount > maxLimit
    }

    private fun isRateLimitedAuthEndpoint(path: String, method: String): Boolean {
        if (method != "POST") return false
        return path == "/api/auth/login" ||
                path == "/api/auth/register" ||
                path == "/api/auth/verify-email" ||
                path == "/api/auth/forgot-password" ||
                path == "/api/auth/reset-password" ||
                path == "/api/auth/refresh" ||
                path == "/api/auth/oauth/google"
    }

    private fun isRateLimitedAiEndpoint(path: String, method: String): Boolean {
        if (method != "POST") return false
        return path == "/api/chat/stream" ||
                (path.startsWith("/api/children/") && path.endsWith("/reports/generate")) ||
                (path.startsWith("/api/reports/") && path.endsWith("/regenerate"))
    }

    private fun extractAiIdentifier(request: HttpServletRequest): String {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ") && jwtService != null) {
            val jwt = authHeader.substring(7)
            val result = jwtService.parseToken(jwt)
            if (result is JwtService.JwtResult.Access) {
                val userId = result.claims.get(JwtService.CLAIM_USER_ID, String::class.java)
                if (userId != null) return "user:$userId"
                val username = result.claims.subject
                if (username != null) return "user:$username"
            }
        }
        return "ip:" + extractClientIp(request)
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
}
