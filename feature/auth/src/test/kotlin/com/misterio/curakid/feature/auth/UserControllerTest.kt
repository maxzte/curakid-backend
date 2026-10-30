package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.auth.Role
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.feature.auth.controller.UserController
import com.misterio.curakid.feature.auth.service.AuthService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class UserControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var authService: AuthService
    private val user = User(email = "test@test.com", passwordHash = "hash", name = "Test", role = Role.ROLE_USER).apply {
        id = UUID.randomUUID()
    }
    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())

    @BeforeEach
    fun setUp() {
        authService = mock()
        val controller = UserController(authService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET me should return user response`() {
        whenever(authService.getUser(eq(user.id))).thenReturn(
            UserResponse(user.id, user.email, user.name, user.role.name, user.isActive, user.onboardingCompleted)
        )

        mockMvc.perform(get("/api/v1/users/me").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("test@test.com"))
    }

    @Test
    fun `PUT me should update user and return response`() {
        whenever(authService.updateUser(eq(user.id), any())).thenReturn(
            UserResponse(user.id, user.email, "Updated Name", user.role.name, user.isActive, user.onboardingCompleted)
        )

        mockMvc.perform(
            put("/api/v1/users/me")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Updated Name"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Updated Name"))
    }

    @Test
    fun `DELETE me should return 204`() {
        mockMvc.perform(delete("/api/v1/users/me").principal(auth))
            .andExpect(status().isNoContent())

        verify(authService).deleteMe(user.id)
    }
}
