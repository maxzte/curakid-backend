package com.misterio.curakid.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

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

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.substring(7)

        try {
            val claims = jwtService.parseValidClaims(jwt)
            val userEmail = claims?.subject ?: jwtService.extractUsername(jwt)
            val jti = claims?.id ?: jwtService.extractJti(jwt)
            val tokenType = claims?.get(JwtService.CLAIM_TOKEN_TYPE, String::class.java)
                ?: jwtService.extractTokenType(jwt)

            if (tokenType != null && tokenType != JwtService.TYPE_ACCESS) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token type")
                return
            }

            if (claims == null && !jwtService.isTokenValid(jwt)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token")
                return
            }

            if (userEmail != null && SecurityContextHolder.getContext().authentication == null) {
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

                val userDetails = userDetailsService.loadUserByUsername(userEmail)
                if (!userDetails.isEnabled || userDetails.username != userEmail) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token or user disabled")
                    return
                }

                val authToken = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                )
                authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authToken
            }
        } catch (e: Exception) {
            logger.error("Authentication failed: ${e.message}", e)
            if (!response.isCommitted) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token")
            }
            return
        }

        filterChain.doFilter(request, response)
    }
}