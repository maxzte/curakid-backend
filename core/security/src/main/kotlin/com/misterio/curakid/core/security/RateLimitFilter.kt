package com.misterio.curakid.core.security

import com.google.common.cache.CacheBuilder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
class RateLimitFilter(
    private val redisTemplate: StringRedisTemplate? = null
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(RateLimitFilter::class.java)

    companion object {
        private const val REDIS_KEY_PREFIX = "ratelimit:auth:"
    }

    private data class RateWindow(
        val count: AtomicInteger = AtomicInteger(0),
        val windowStart: Long = System.currentTimeMillis()
    )

    // Window: 1 minute. Cleaned automatically by Guava cache as fallback.
    private val fallbackAuthRateLimitCache = CacheBuilder.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build<String, RateWindow>()

    // Allow max 15 auth requests per minute per IP to prevent credential stuffing / brute force
    private val maxAuthRequestsPerMinute = 15

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI

        if (isRateLimitedAuthEndpoint(path, request.method)) {
            val clientIp = extractClientIp(request)
            val isExceeded = if (redisTemplate != null) {
                checkWithRedis(clientIp)
            } else {
                checkWithFallback(clientIp)
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

    private fun checkWithRedis(clientIp: String): Boolean {
        val key = REDIS_KEY_PREFIX + clientIp
        return try {
            val count = redisTemplate!!.opsForValue().increment(key) ?: 1L
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1))
            }
            count > maxAuthRequestsPerMinute
        } catch (e: Exception) {
            log.warn("Redis rate-limiting failed for {}, falling back to local memory: {}", clientIp, e.message)
            checkWithFallback(clientIp)
        }
    }

    private fun checkWithFallback(clientIp: String): Boolean {
        val window = fallbackAuthRateLimitCache.get(clientIp) { RateWindow() }
        val currentCount = window.count.incrementAndGet()
        return currentCount > maxAuthRequestsPerMinute
    }

    private fun isRateLimitedAuthEndpoint(path: String, method: String): Boolean {
        if (method != "POST") return false
        return path == "/api/auth/login" ||
                path == "/api/auth/register" ||
                path == "/api/auth/verify-email" ||
                path == "/api/auth/forgot-password" ||
                path == "/api/auth/reset-password"
    }

    private fun extractClientIp(request: HttpServletRequest): String {
        // X-Real-IP is typically set directly by the ingress/reverse-proxy
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp.trim()
        }

        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            // In a standard proxy chain, the rightmost client IP before internal hops is the most reliable
            val ips = xForwardedFor.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (ips.isNotEmpty()) {
                return ips.first()
            }
        }
        return request.remoteAddr ?: "unknown"
    }
}
