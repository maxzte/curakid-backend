package com.misterio.curakid.security

import com.misterio.curakid.core.database.auth.Role
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.security.JwtService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class JwtServiceTest {

    private lateinit var jwtService: JwtService
    private val secret = "mySecretKeyForTestingMustBeAtLeast32CharactersLong!!"

    @BeforeEach
    fun setUp() {
        jwtService = JwtService(
            secret = secret,
            accessExpiration = 900000L,
            refreshExpiration = 604800000L
        )
    }

    @Test
    fun `should generate and validate access token as JwtResult Access`() {
        val user = User(email = "test@test.com", role = Role.ROLE_USER).apply { id = UUID.randomUUID() }

        val token = jwtService.generateAccessToken(user)
        assertNotNull(token)

        val result = jwtService.parseToken(token)
        assertTrue(result is JwtService.JwtResult.Access)

        val claims = (result as JwtService.JwtResult.Access).claims
        assertEquals("test@test.com", claims.subject)
        assertNotNull(claims.id)
        assertEquals(user.id.toString(), claims.get(JwtService.CLAIM_USER_ID, String::class.java))
        assertEquals(Role.ROLE_USER.name, claims.get(JwtService.CLAIM_ROLE, String::class.java))
    }

    @Test
    fun `should generate and validate refresh token as JwtResult Refresh`() {
        val user = User(email = "refresh@test.com").apply { id = UUID.randomUUID() }

        val token = jwtService.generateRefreshToken(user)
        assertNotNull(token)

        val result = jwtService.parseToken(token)
        assertTrue(result is JwtService.JwtResult.Refresh)

        val claims = (result as JwtService.JwtResult.Refresh).claims
        assertEquals("refresh@test.com", claims.subject)
        assertNotNull(claims.id)
    }

    @Test
    fun `parseToken should return Invalid for invalid or tampered token`() {
        val result = jwtService.parseToken("invalid.token.string")
        assertTrue(result is JwtService.JwtResult.Invalid)
    }

    @Test
    fun `parseToken should return Expired for expired token`() {
        val expiredJwtService = JwtService(
            secret = secret,
            accessExpiration = -1000L,
            refreshExpiration = -1000L
        )
        val user = User(email = "expired@test.com").apply { id = UUID.randomUUID() }
        val token = expiredJwtService.generateAccessToken(user)

        val result = jwtService.parseToken(token)
        assertTrue(result is JwtService.JwtResult.Expired)

        val claims = jwtService.getClaimsIgnoringExpiration(token)
        assertNotNull(claims)
        assertEquals("expired@test.com", claims?.subject)
    }
}
