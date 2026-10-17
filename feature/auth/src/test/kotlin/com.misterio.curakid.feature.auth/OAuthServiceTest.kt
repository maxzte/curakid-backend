package com.misterio.curakid.feature.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.token.RefreshToken
import com.misterio.curakid.core.database.token.RefreshTokenRepository
import com.misterio.curakid.core.security.JwtService
import com.misterio.curakid.feature.auth.service.OAuthService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito.mockConstruction
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuthServiceTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var oauthProviderRepository: OAuthProviderRepository
    @Mock lateinit var jwtService: JwtService
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var mockedConstruction: MockedConstruction<GoogleIdTokenVerifier>
    private lateinit var service: OAuthService

    @BeforeEach
    fun setUp() {
        mockedConstruction = mockConstruction(GoogleIdTokenVerifier::class.java) { verifierMock, _ ->
            val idToken = mock<GoogleIdToken>()
            val payload = mock<GoogleIdToken.Payload>()
            whenever(verifierMock.verify(any<String>())).thenReturn(idToken)
            whenever(idToken.payload).thenReturn(payload)
            whenever(payload.email).thenReturn("test@gmail.com")
            whenever(payload.subject).thenReturn("google-sub-123")
            whenever(payload.get("name")).thenReturn("Test User")
        }

        service = OAuthService(
            "test-client-id.apps.googleusercontent.com",
            userRepository,
            oauthProviderRepository,
            jwtService,
            refreshTokenRepository
        )
        whenever(jwtService.refreshExpiration).thenReturn(604800000L)
    }

    @AfterEach
    fun tearDown() {
        mockedConstruction.close()
    }

    @Test
    fun `loginWithGoogle should register new user and return tokens`() {
        whenever(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty())
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(oauthProviderRepository.findByProviderNameAndProviderUserId(any(), any())).thenReturn(Optional.empty())
        whenever(oauthProviderRepository.save(any<OAuthProvider>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateAccessToken(any())).thenReturn("access-token")
        whenever(jwtService.generateRefreshToken(any())).thenReturn("refresh-token")
        whenever(refreshTokenRepository.findByUser(any())).thenReturn(Optional.empty())
        whenever(refreshTokenRepository.save(any<RefreshToken>())).thenAnswer { it.arguments[0] }

        val response = service.loginWithGoogle("valid-id-token")

        assertEquals("access-token", response.accessToken)
        assertEquals("refresh-token", response.refreshToken)
        verify(userRepository).save(any())
        verify(oauthProviderRepository).save(any())
    }

    @Test
    fun `loginWithGoogle should return existing user when email already registered`() {
        val existingUser = User(email = "test@gmail.com", name = "Existing").apply { id = UUID.randomUUID() }
        whenever(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(existingUser))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(oauthProviderRepository.findByProviderNameAndProviderUserId(any(), any())).thenReturn(Optional.empty())
        whenever(oauthProviderRepository.save(any<OAuthProvider>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateAccessToken(any())).thenReturn("access-token")
        whenever(jwtService.generateRefreshToken(any())).thenReturn("refresh-token")
        whenever(refreshTokenRepository.findByUser(any())).thenReturn(Optional.empty())
        whenever(refreshTokenRepository.save(any<RefreshToken>())).thenAnswer { it.arguments[0] }

        val response = service.loginWithGoogle("valid-id-token")

        assertEquals("access-token", response.accessToken)
    }

    @Test
    fun `loginWithGoogle should reuse existing OAuth provider link`() {
        whenever(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.empty())
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        val existingProvider = OAuthProvider(providerName = "google", providerUserId = "google-sub-123").apply { id = UUID.randomUUID() }
        whenever(oauthProviderRepository.findByProviderNameAndProviderUserId("google", "google-sub-123")).thenReturn(Optional.of(existingProvider))
        whenever(jwtService.generateAccessToken(any())).thenReturn("access-token")
        whenever(jwtService.generateRefreshToken(any())).thenReturn("refresh-token")
        whenever(refreshTokenRepository.findByUser(any())).thenReturn(Optional.empty())
        whenever(refreshTokenRepository.save(any<RefreshToken>())).thenAnswer { it.arguments[0] }

        service.loginWithGoogle("valid-id-token")

        verify(oauthProviderRepository).findByProviderNameAndProviderUserId("google", "google-sub-123")
    }

    @Test
    fun `loginWithGoogle should throw when token verification fails`() {
        val mockVerifier = mockedConstruction.constructed().first()
        whenever(mockVerifier.verify(any<String>())).thenReturn(null)

        val exception = assertThrows<RuntimeException> {
            service.loginWithGoogle("invalid-token")
        }
        assertTrue(exception.message!!.contains("Invalid Google ID Token"))
    }

    @Test
    fun `loginWithGoogle should activate unverified user`() {
        val unverifiedUser = User(email = "test@gmail.com", name = "Pending", isActive = false).apply {
            id = UUID.randomUUID()
            verificationCode = "123456"
        }
        whenever(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(unverifiedUser))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }
        whenever(oauthProviderRepository.findByProviderNameAndProviderUserId(any(), any())).thenReturn(Optional.empty())
        whenever(oauthProviderRepository.save(any<OAuthProvider>())).thenAnswer { it.arguments[0] }
        whenever(jwtService.generateAccessToken(any())).thenReturn("access-token")
        whenever(jwtService.generateRefreshToken(any())).thenReturn("refresh-token")
        whenever(refreshTokenRepository.findByUser(any())).thenReturn(Optional.empty())
        whenever(refreshTokenRepository.save(any<RefreshToken>())).thenAnswer { it.arguments[0] }

        val response = service.loginWithGoogle("valid-id-token")

        assertEquals("access-token", response.accessToken)
        assertTrue(unverifiedUser.isActive, "User should be activated on Google login")
        assertEquals(null, unverifiedUser.verificationCode)
        verify(userRepository).save(unverifiedUser)
    }

    @Test
    fun `loginWithGoogle should reject deleted user`() {
        val deletedUser = User(email = "test@gmail.com", name = "Deleted").apply {
            id = UUID.randomUUID()
            deletedAt = java.time.Instant.now()
        }
        whenever(userRepository.findByEmail("test@gmail.com")).thenReturn(Optional.of(deletedUser))

        assertThrows<org.springframework.web.server.ResponseStatusException> {
            service.loginWithGoogle("valid-id-token")
        }
    }
}
