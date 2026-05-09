package com.misterio.curakid.feature.profile

import com.misterio.curakid.feature.profile.domain.UserEntity
import com.misterio.curakid.feature.profile.domain.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper

@SpringBootTest(classes = [ProfileAppTest::class])
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class ProfileControllerTest @Autowired constructor(
    var mockMvc: MockMvc,
    val objectMapper: ObjectMapper
) {

    @Test
    fun `should create, get by id and get all users`() {
        val userId = UserId(1L, "test@misterio.com")
        val user = UserEntity(userId, "Misterio", "pass123")

        // 1. Тест додавання (POST)
        mockMvc.post("/profile/register") {
//            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(user)
        }.andExpect {
            status { isOk() }
            jsonPath("$.nick") { value("Misterio") }
        }

        // 2. Тест отримання за ID (GET)
        // Передаємо параметри в URL, як визначено в контролері
        mockMvc.get("/profile/${userId.id}/${userId.email}") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }

        // 3. Тест отримання всіх (GET)
        mockMvc.get("/profile/all") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$") { isArray() }
            jsonPath("$.length()") { value(1) }
        }
    }
}