package com.misterio.curakid.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

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

        if (!path.startsWith("/admin/ui/") || path == "/admin/ui/login") {
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
            val email = jwtService.extractUsername(token) ?: return
            val user = userDetailsService.loadUserByUsername(email)

            val isAdmin = user.authorities.any { it.authority == "ROLE_ADMIN" }
            if (!isAdmin) {
                response.sendRedirect("/admin/ui/login")
                return
            }
        } catch (e: Exception) {
            response.sendRedirect("/admin/ui/login")
            return
        }

        filterChain.doFilter(request, response)
    }
}
