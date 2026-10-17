package com.misterio.curakid.security

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.security.JwtAuthenticationFilter
import com.misterio.curakid.core.security.JwtService
import com.misterio.curakid.core.security.TokenBlacklistService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService

class JwtAuthenticationFilterTest {

    private lateinit var jwtService: JwtService
    private lateinit var userDetailsService: UserDetailsService
    private lateinit var tokenBlacklistService: TokenBlacklistService
    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        jwtService = mock()
        userDetailsService = mock()
        tokenBlacklistService = mock()
        filter = JwtAuthenticationFilter(jwtService, userDetailsService, tokenBlacklistService)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should pass through when no Authorization header`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        verify(jwtService, never()).isTokenValid(any<String>())
    }

    @Test
    fun `should pass through when Authorization header does not start with Bearer`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Basic dXNlcjpwYXNz")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
    }

    @Test
    fun `should send 401 when token is invalid`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer invalid-jwt")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()

        whenever(jwtService.isTokenValid("invalid-jwt")).thenReturn(false)

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        verify(chain, never()).doFilter(any(), any())
    }

    @Test
    fun `should send 401 when token is blacklisted`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer valid-but-blacklisted")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()

        whenever(jwtService.isTokenValid("valid-but-blacklisted")).thenReturn(true)
        whenever(jwtService.extractUsername("valid-but-blacklisted")).thenReturn("test@test.com")
        whenever(jwtService.extractJti("valid-but-blacklisted")).thenReturn("jti-123")
        whenever(tokenBlacklistService.isBlacklisted("jti-123")).thenReturn(true)

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        verify(chain, never()).doFilter(any(), any())
    }

    @Test
    fun `should authenticate when token is valid and not blacklisted`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer valid-token")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()
        val user = User(email = "test@test.com", passwordHash = "hash", name = "Test", isActive = true).apply {
            id = java.util.UUID.randomUUID()
        }

        whenever(jwtService.isTokenValid("valid-token")).thenReturn(true)
        whenever(jwtService.extractUsername("valid-token")).thenReturn("test@test.com")
        whenever(jwtService.extractJti("valid-token")).thenReturn("jti-456")
        whenever(jwtService.isTokenValid("valid-token", user)).thenReturn(true)
        whenever(tokenBlacklistService.isBlacklisted("jti-456")).thenReturn(false)
        whenever(userDetailsService.loadUserByUsername("test@test.com")).thenReturn(user)

        filter.doFilter(request, response, chain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertEquals(user, authentication!!.principal)
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `should send 401 when user details do not match token`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer token-for-different-user")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()
        val storedUser = User(email = "stored@test.com", passwordHash = "hash", name = "Stored").apply {
            id = java.util.UUID.randomUUID()
        }

        whenever(jwtService.isTokenValid("token-for-different-user")).thenReturn(true)
        whenever(jwtService.extractUsername("token-for-different-user")).thenReturn("token@test.com")
        whenever(jwtService.extractJti("token-for-different-user")).thenReturn("jti-789")
        whenever(tokenBlacklistService.isBlacklisted("jti-789")).thenReturn(false)
        whenever(userDetailsService.loadUserByUsername("token@test.com")).thenReturn(storedUser)
        whenever(jwtService.isTokenValid("token-for-different-user", storedUser)).thenReturn(false)

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        verify(chain, never()).doFilter(any(), any())
    }

    @Test
    fun `should send 401 when token type is refresh instead of access`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer refresh-token-as-access")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()

        whenever(jwtService.parseValidClaims("refresh-token-as-access")).thenReturn(null)
        whenever(jwtService.extractTokenType("refresh-token-as-access")).thenReturn(JwtService.TYPE_REFRESH)

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        verify(chain, never()).doFilter(any(), any())
    }

    @Test
    fun `should send 401 when user is disabled`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer valid-token")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()
        val disabledUser = User(email = "test@test.com", passwordHash = "hash", name = "Test", isActive = false).apply {
            id = java.util.UUID.randomUUID()
        }

        whenever(jwtService.isTokenValid("valid-token")).thenReturn(true)
        whenever(jwtService.extractUsername("valid-token")).thenReturn("test@test.com")
        whenever(jwtService.extractJti("valid-token")).thenReturn("jti-456")
        whenever(tokenBlacklistService.isBlacklisted("jti-456")).thenReturn(false)
        whenever(userDetailsService.loadUserByUsername("test@test.com")).thenReturn(disabledUser)

        filter.doFilter(request, response, chain)

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status)
        verify(chain, never()).doFilter(any(), any())
    }
}
