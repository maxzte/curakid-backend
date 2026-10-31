package com.misterio.curakid.core.security

import com.misterio.curakid.core.database.auth.Role
import com.misterio.curakid.core.database.auth.User
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService,
    private val tokenBlacklistService: TokenBlacklistService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val jwt = authHeader.substring(7)

            when (val result = jwtService.parseToken(jwt)) {
                is JwtService.JwtResult.Refresh -> {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Refresh tokens cannot be used as Access tokens")
                    return
                }
                is JwtService.JwtResult.Access -> {
                    val claims = result.claims
                    val jti = claims.id
                    val userEmail = claims.subject

                    val blacklisted = try {
                        jti != null && tokenBlacklistService.isBlacklisted(jti)
                    } catch (e: Exception) {
                        logger.error("Blacklist check failed: ${e.message}", e)
                        false
                    }

                    if (blacklisted) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked")
                        return
                    }

                    val userIdStr = claims.get(JwtService.CLAIM_USER_ID, String::class.java)
                    val roleStr = claims.get(JwtService.CLAIM_ROLE, String::class.java)

                    val userDetails: UserDetails? = if (userIdStr != null && roleStr != null) {
                        try {
                            val role = Role.valueOf(roleStr)
                            User(
                                email = userEmail,
                                passwordHash = "",
                                name = userEmail,
                                role = role,
                                isActive = true
                            ).apply {
                                setId(UUID.fromString(userIdStr))
                            }
                        } catch (_: Exception) {
                            try {
                                userDetailsService.loadUserByUsername(userEmail)
                            } catch (_: Exception) { null }
                        }
                    } else {
                        try {
                            userDetailsService.loadUserByUsername(userEmail)
                        } catch (_: Exception) { null }
                    }

                    if (userDetails == null || !userDetails.isEnabled || userDetails.username != userEmail) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token or user disabled")
                        return
                    }

                    val authToken = UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.authorities
                    )
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                }
                is JwtService.JwtResult.Expired, is JwtService.JwtResult.Invalid -> {
                    // Do not set SecurityContext; let filterChain proceed.
                    // Public endpoints (e.g., /auth/login) will be allowed.
                    // Protected endpoints will be blocked by AuthorizationFilter.
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
