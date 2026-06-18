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
    fun `should generate and validate access token`() {
        val user = User(email = "test@test.com", role = Role.ROLE_USER).apply { id = UUID.randomUUID() }

        val token = jwtService.generateAccessToken(user)
        assertNotNull(token)

        val email = jwtService.extractUsername(token)
        assertEquals("test@test.com", email)

        val jti = jwtService.extractJti(token)
        assertNotNull(jti)

        assertTrue(jwtService.isTokenValid(token, user))
    }

    @Test
    fun `should generate and validate refresh token`() {
        val user = User(email = "refresh@test.com").apply { id = UUID.randomUUID() }
        val token = jwtService.generateRefreshToken(user)

        assertEquals("refresh@test.com", jwtService.extractUsername(token))
        assertTrue(jwtService.isTokenValid(token, user))
    }

    @Test
    fun `isTokenValid should return false for different user`() {
        val user1 = User(email = "user1@test.com").apply { id = UUID.randomUUID() }
        val user2 = User(email = "user2@test.com").apply { id = UUID.randomUUID() }

        val token = jwtService.generateAccessToken(user1)
        assertFalse(jwtService.isTokenValid(token, user2))
    }
}
