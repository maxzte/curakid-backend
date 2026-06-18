package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.feature.auth.controller.AuthController
import com.misterio.curakid.feature.auth.service.AuthService
import com.misterio.curakid.feature.auth.service.OAuthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class AuthControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var authService: AuthService
    private lateinit var oauthService: OAuthService

    @BeforeEach
    fun setUp() {
        authService = mock()
        oauthService = mock()
        val controller = AuthController(authService, oauthService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `POST register should return 200`() {
        whenever(authService.register(any(), any())).thenReturn(MessageResponse("Success"))

        mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","password":"pass","name":"Test","consent":{"termsAccepted":true,"privacyAccepted":true,"consentTimestamp":"2026-01-01T00:00:00Z","consentVersion":"1.0","appVersion":"1.0.0","deviceLocale":"uk-UA"}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Success"))
    }

    @Test
    fun `POST login should return tokens`() {
        whenever(authService.login(any())).thenReturn(
            AuthResponse(accessToken = "at", refreshToken = "rt", onboardingCompleted = true)
        )

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","password":"pass"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("at"))
            .andExpect(jsonPath("$.refreshToken").value("rt"))
    }

    @Test
    fun `POST refresh should return new tokens`() {
        whenever(authService.refreshToken(any())).thenReturn(
            AuthResponse(accessToken = "new-at", refreshToken = "new-rt", onboardingCompleted = true)
        )

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"old-rt"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("new-at"))
    }

    @Test
    fun `POST logout should return 204`() {
        mockMvc.perform(
            post("/api/auth/logout")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"rt"}""")
        )
            .andExpect(status().isNoContent())
    }

    @Test
    fun `POST verify-email should return tokens`() {
        whenever(authService.verifyEmail(any())).thenReturn(
            AuthResponse(accessToken = "at", refreshToken = "rt", onboardingCompleted = false)
        )

        mockMvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","code":"123456"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("at"))
    }

    @Test
    fun `POST forgot-password should return message`() {
        whenever(authService.forgotPassword(any())).thenReturn(MessageResponse("Check email"))

        mockMvc.perform(
            post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Check email"))
    }

    @Test
    fun `POST reset-password should return message`() {
        whenever(authService.resetPassword(any())).thenReturn(MessageResponse("Password reset"))

        mockMvc.perform(
            post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","code":"123456","newPassword":"newpass"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Password reset"))
    }

    @Test
    fun `POST oauth google should return tokens`() {
        whenever(oauthService.loginWithGoogle(eq("google-token"))).thenReturn(
            AuthResponse(accessToken = "at", refreshToken = "rt", onboardingCompleted = true)
        )

        mockMvc.perform(
            post("/api/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"google-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("at"))
    }

    @Test
    fun `DELETE delete me should return 204`() {
        val user = User(email = "test@test.com", passwordHash = "hash", name = "Test").apply {
            id = UUID.randomUUID()
        }
        val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())

        mockMvc.perform(delete("/api/auth/delete/me").principal(auth))
            .andExpect(status().isNoContent())

        verify(authService).deleteMe(user.id!!)
    }
}
