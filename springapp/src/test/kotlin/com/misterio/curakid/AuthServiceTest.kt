package com.misterio.curakid

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.token.RefreshToken
import com.misterio.curakid.core.database.token.RefreshTokenRepository
import com.misterio.curakid.core.security.JwtService
import com.misterio.curakid.core.security.TokenBlacklistService
import com.misterio.curakid.feature.auth.*
import io.jsonwebtoken.Claims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.*

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var jwtService: JwtService
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var tokenBlacklistService: TokenBlacklistService
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        userRepository = mock()
        refreshTokenRepository = mock()
        jwtService = mock()
        passwordEncoder = mock()
        authenticationManager = mock()
        tokenBlacklistService = mock()
        authService = AuthService(
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            passwordEncoder = passwordEncoder,
            jwtService = jwtService,
            authenticationManager = authenticationManager,
            tokenBlacklistService = tokenBlacklistService,
            604800000L,
        )
    }

    @Test
    fun `register should throw if email already exists`() {
        val request = RegisterRequest("test@test.com", "pass", "Name")
        whenever(userRepository.existsByEmail(any())).thenReturn(true)

        assertThrows<RuntimeException> {
            authService.register(request)
        }
    }

    @Test
    fun `login should return tokens on success`() {
        val request = LoginRequest("test@test.com", "pass")
        val user =
            User(email = "test@test.com", passwordHash = "hash", name = "Name").apply { id = UUID.randomUUID() }

        whenever(userRepository.findByEmail(any())).thenReturn(Optional.of(user))
        whenever(jwtService.generateAccessToken(user)).thenReturn("access-token")
        whenever(jwtService.generateRefreshToken(user)).thenReturn("refresh-token")

        val response = authService.login(request)

        assertEquals("access-token", response.accessToken)
        assertEquals("refresh-token", response.refreshToken)
        verify(refreshTokenRepository).save(any())
    }

    @Test
    fun `login should throw on bad credentials`() {
        val request = LoginRequest("test@test.com", "wrong")
        whenever(authenticationManager.authenticate(any())).thenThrow(BadCredentialsException("Bad credentials"))

        assertThrows<BadCredentialsException> {
            authService.login(request)
        }
    }

    @Test
    fun `refreshToken should return new access token if valid`() {
        val refreshTokenStr = "valid-refresh-token"
        val user = User(email = "test@test.com").apply { id = UUID.randomUUID() }
        val refreshTokenObj =
            RefreshToken(user = user, token = refreshTokenStr, expiryDate = Instant.now().plusSeconds(3600))

        whenever(refreshTokenRepository.findByToken(refreshTokenStr)).thenReturn(Optional.of(refreshTokenObj))
        whenever(jwtService.generateAccessToken(user)).thenReturn("new-access-token")
        whenever(jwtService.generateRefreshToken(user)).thenReturn(refreshTokenStr)

        val response = authService.refreshToken(RefreshTokenRequest(refreshTokenStr))

        assertEquals("new-access-token", response.accessToken)
        assertEquals(refreshTokenStr, response.refreshToken)
    }

    @Test
    fun `logout should blacklist token and delete refresh token`() {
        val authHeader = "Bearer token.jwt"
        val refreshToken = "refresh-token"
        whenever(jwtService.extractJti("token.jwt")).thenReturn("jti-123")
        whenever(jwtService.extractClaim("token.jwt", Claims::getSubject))
            .thenReturn(Date(System.currentTimeMillis() + 10000).toString())

        val mockUser =
            User(email = "test@test.com", passwordHash = "hash", name = "Name").apply { id = UUID.randomUUID() }
        val mockToken = RefreshToken(user = mockUser, token = refreshToken, expiryDate = Instant.now())
        whenever(refreshTokenRepository.findByToken(refreshToken)).thenReturn(Optional.of(mockToken))

        authService.logout(authHeader, refreshToken)

        verify(tokenBlacklistService).addToBlacklist(eq("jti-123"), any())
        verify(refreshTokenRepository).delete(mockToken)
    }
}
