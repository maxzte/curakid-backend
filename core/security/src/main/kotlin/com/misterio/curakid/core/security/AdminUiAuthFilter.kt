package com.misterio.curakid.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource

@Component
class AdminUiAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI

        val isAdminPath = path == "/admin/ui" || path.startsWith("/admin/ui/")
        if (!isAdminPath || path == "/admin/ui/login") {
            filterChain.doFilter(request, response)
            return
        }

        val token = request.cookies
            ?.find { it.name == "admin_token" }
            ?.value

        if (token == null) {
            response.sendRedirect("/admin/ui/login")
            return
        }

        try {
            if (!jwtService.isTokenValid(token)) {
                response.sendRedirect("/admin/ui/login")
                return
            }

            val email = jwtService.extractUsername(token)
            if (email == null) {
                response.sendRedirect("/admin/ui/login")
                return
            }

            val user = userDetailsService.loadUserByUsername(email)
            val isAdmin = user.authorities.any { it.authority == "ROLE_ADMIN" }
            if (!isAdmin) {
                response.sendRedirect("/admin/ui/login")
                return
            }

            val authToken = UsernamePasswordAuthenticationToken(user, null, user.authorities)
            authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = authToken
        } catch (e: Exception) {
            response.sendRedirect("/admin/ui/login")
            return
        }

        filterChain.doFilter(request, response)
    }
}