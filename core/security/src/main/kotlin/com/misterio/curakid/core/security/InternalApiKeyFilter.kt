package com.misterio.curakid.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
class InternalApiKeyFilter(
    @Value($$"${internal-api-key:${INTERNAL_API_KEY:}}")
    private val internalApiKey: String
) : OncePerRequestFilter() {

    private val pathMatcher = AntPathMatcher()
    private val internalPatterns = listOf(
        "/api/v1/notifications/test-push",
        "/api/v1/notifications/test-push/*",
        "/api/v1/notifications/trigger-reminders",
        "/api/v1/notifications/trigger-reminders/*"
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return internalPatterns.none { pattern -> pathMatcher.match(pattern, path) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val apiKey = request.getHeader("X-Internal-Api-Key")
        if (internalApiKey.isBlank() || apiKey == null || !MessageDigest.isEqual(apiKey.toByteArray(Charsets.UTF_8), internalApiKey.toByteArray(Charsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
            return
        }

        filterChain.doFilter(request, response)
    }
}

