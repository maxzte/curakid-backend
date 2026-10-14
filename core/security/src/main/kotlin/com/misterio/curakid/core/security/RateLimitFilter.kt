package com.misterio.curakid.core.security

import com.google.common.cache.CacheBuilder
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Component
class RateLimitFilter : OncePerRequestFilter() {

    private data class RateWindow(
        val count: AtomicInteger = AtomicInteger(0),
        val windowStart: Long = System.currentTimeMillis()
    )

    // Window: 1 minute. Cleaned automatically by Guava cache.
    private val authRateLimitCache = CacheBuilder.newBuilder()
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
            val window = authRateLimitCache.get(clientIp) { RateWindow() }

            val currentCount = window.count.incrementAndGet()
            if (currentCount > maxAuthRequestsPerMinute) {
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

    private fun isRateLimitedAuthEndpoint(path: String, method: String): Boolean {
        if (method != "POST") return false
        return path == "/api/auth/login" ||
                path == "/api/auth/register" ||
                path == "/api/auth/forgot-password" ||
                path == "/api/auth/reset-password"
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
