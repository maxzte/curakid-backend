package com.misterio.curakid.feature.children

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.feature.children.ChildController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate
import java.util.UUID

class ChildControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var childService: ChildService
    private val user = User(email = "test@test.com", passwordHash = "hash", name = "Test").apply {
        id = UUID.randomUUID()
    }
    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())

    @BeforeEach
    fun setUp() {
        childService = mock()
        val controller = ChildController(childService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET children returns list`() {
        val childId = UUID.randomUUID()
        whenever(childService.getChildren(any())).thenReturn(
            listOf(ChildResponse(childId, "Alice", LocalDate.of(2024, 1, 1), Gender.FEMALE, false, null, ageInMonths = 17))
        )

        mockMvc.perform(get("/api/children").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("Alice"))
    }

    @Test
    fun `GET child by id returns child`() {
        val childId = UUID.randomUUID()
        whenever(childService.getChild(childId, user)).thenReturn(
            ChildResponse(childId, "Bob", LocalDate.of(2023, 6, 15), Gender.MALE, false, null, ageInMonths = 24)
        )

        mockMvc.perform(get("/api/children/$childId").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Bob"))
    }

    @Test
    fun `POST child returns 201`() {
        val childId = UUID.randomUUID()
        whenever(childService.createChild(any(), any())).thenReturn(
            ChildResponse(childId, "Charlie", LocalDate.of(2024, 3, 10), Gender.MALE, false, null, ageInMonths = 15)
        )

        mockMvc.perform(
            post("/api/children")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"$childId","name":"Charlie","birthDate":"2024-03-10","gender":"MALE","isPremature":false}""")
                .principal(auth)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Charlie"))
    }

    @Test
    fun `PUT child returns updated`() {
        val childId = UUID.randomUUID()
        whenever(childService.updateChild(any(), any(), any())).thenReturn(
            ChildResponse(childId, "Charlie Updated", LocalDate.of(2024, 3, 10), Gender.MALE, false, null, ageInMonths = 15)
        )

        mockMvc.perform(
            put("/api/children/$childId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Charlie Updated","birthDate":"2024-03-10","gender":"MALE","isPremature":false}""")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Charlie Updated"))
    }

    @Test
    fun `DELETE child returns 204`() {
        val childId = UUID.randomUUID()

        mockMvc.perform(delete("/api/children/$childId").principal(auth))
            .andExpect(status().isNoContent())

        verify(childService).deleteChild(childId, user)
    }
}
