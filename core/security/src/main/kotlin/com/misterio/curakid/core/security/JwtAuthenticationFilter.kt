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

            try {
                val claims = jwtService.parseValidClaims(jwt)
                val userEmail = claims?.subject ?: jwtService.extractUsername(jwt)
                val jti = claims?.id ?: jwtService.extractJti(jwt)
                val tokenType = claims?.get(JwtService.CLAIM_TOKEN_TYPE, String::class.java)
                    ?: jwtService.extractTokenType(jwt)

                val isValidType = tokenType == null || tokenType == JwtService.TYPE_ACCESS
                val isValidToken = claims != null || jwtService.isTokenValid(jwt)

                if (isValidType && isValidToken && userEmail != null && SecurityContextHolder.getContext().authentication == null) {
                    val blacklisted = try {
                        jti != null && tokenBlacklistService.isBlacklisted(jti)
                    } catch (e: Exception) {
                        logger.error("Blacklist check failed: ${e.message}", e)
                        false
                    }

                    if (!blacklisted) {
                        val userIdStr = claims?.get(JwtService.CLAIM_USER_ID, String::class.java)
                        val roleStr = claims?.get(JwtService.CLAIM_ROLE, String::class.java)

                        val userDetails: UserDetails = if (userIdStr != null && roleStr != null) {
                            // Fully stateless path: no database query needed for 1M DAU peak traffic
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
                                userDetailsService.loadUserByUsername(userEmail)
                            }
                        } else {
                            // Fallback to database lookup for legacy tokens without uid/role claim
                            userDetailsService.loadUserByUsername(userEmail)
                        }

                        if (userDetails.isEnabled && userDetails.username == userEmail) {
                            val authToken = UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.authorities
                            )
                            authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                            SecurityContextHolder.getContext().authentication = authToken
                        }
                    }
                }
            } catch (e: Exception) {
                logger.debug("JWT validation failed: ${e.message}")
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }
}
