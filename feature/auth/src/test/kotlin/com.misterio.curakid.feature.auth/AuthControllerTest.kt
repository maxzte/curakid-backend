package com.misterio.curakid.feature.auth

import com.misterio.curakid.feature.auth.controller.AuthController
import com.misterio.curakid.feature.auth.service.AuthService
import com.misterio.curakid.feature.auth.service.OAuthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

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
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","password":"securepassword123","name":"Test","consent":{"termsAccepted":true,"privacyAccepted":true,"consentTimestamp":"2026-01-01T00:00:00Z","consentVersion":"1.0","appVersion":"1.0.0","deviceLocale":"uk-UA"}}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Success"))
    }

    @Test
    fun `POST verify-email should return tokens`() {
        whenever(authService.verifyEmail(any())).thenReturn(
            AuthResponse(accessToken = "at", refreshToken = "rt", onboardingCompleted = false)
        )

        mockMvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","code":"123456"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("at"))
            .andExpect(jsonPath("$.onboardingCompleted").value(false))
    }

    @Test
    fun `POST login should return tokens`() {
        whenever(authService.login(any())).thenReturn(
            AuthResponse(accessToken = "at", refreshToken = "rt", onboardingCompleted = true)
        )

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","password":"securepassword123"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("at"))
            .andExpect(jsonPath("$.onboardingCompleted").value(true))
    }

    @Test
    fun `POST refresh should return new tokens`() {
        whenever(authService.refreshToken(any())).thenReturn(
            AuthResponse(accessToken = "new-at", refreshToken = "new-rt", onboardingCompleted = true)
        )

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"old-rt"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("new-at"))
    }

    @Test
    fun `POST forgot-password should return success`() {
        whenever(authService.forgotPassword(any())).thenReturn(MessageResponse("Reset code sent"))

        mockMvc.perform(
            post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Reset code sent"))
    }

    @Test
    fun `POST reset-password should return success`() {
        whenever(authService.resetPassword(any())).thenReturn(MessageResponse("Password reset"))

        mockMvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"test@test.com","code":"123456","newPassword":"newpassword123"}""")
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
            post("/api/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"idToken":"google-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("at"))
    }
}
