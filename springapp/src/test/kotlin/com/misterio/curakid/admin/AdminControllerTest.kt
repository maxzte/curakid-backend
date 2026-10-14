package com.misterio.curakid.admin

import com.misterio.curakid.admin.controller.AdminController
import com.misterio.curakid.admin.service.AdminFuzzyService
import com.misterio.curakid.admin.service.AdminKnowledgeService
import com.misterio.curakid.admin.service.AdminLegalService
import com.misterio.curakid.admin.service.AdminQuizService
import com.misterio.curakid.admin.service.AdminUserService
import com.misterio.curakid.admin.service.AdminVaccineService
import com.misterio.curakid.core.database.event.VaccineTemplate
import com.misterio.curakid.core.database.event.VaccineType
import com.misterio.curakid.core.database.knowledge.FuzzyDomain
import com.misterio.curakid.core.database.knowledge.FuzzyRuleEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.anyOrNull
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID

class AdminControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var knowledgeService: AdminKnowledgeService
    private lateinit var quizService: AdminQuizService
    private lateinit var fuzzyService: AdminFuzzyService
    private lateinit var userService: AdminUserService
    private lateinit var vaccineService: AdminVaccineService
    private lateinit var legalService: AdminLegalService

    @BeforeEach
    fun setUp() {
        knowledgeService = mock()
        quizService = mock()
        fuzzyService = mock()
        userService = mock()
        vaccineService = mock()
        legalService = mock()
        val controller = AdminController(
            knowledgeService, quizService, fuzzyService,
            userService, vaccineService, legalService, ObjectMapper()
        )
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET knowledge categories returns list`() {
        whenever(knowledgeService.getCategories()).thenReturn(emptyList())

        mockMvc.perform(get("/api/admin/knowledge/categories"))
            .andExpect(status().isOk)
    }

    @Test
    fun `POST knowledge category returns created`() {
        whenever(knowledgeService.createCategory(any())).thenAnswer {
            val req = it.arguments[0] as CreateCategoryRequest
            AdminCategoryResponse(
                id = UUID.randomUUID(),
                name = req.name,
                displayName = req.displayName
            )
        }

        mockMvc.perform(
            post("/api/admin/knowledge/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"test","displayName":"Test"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("test"))
    }

    @Test
    fun `DELETE knowledge article returns 204`() {
        mockMvc.perform(delete("/api/admin/knowledge/${UUID.randomUUID()}"))
            .andExpect(status().isNoContent())
    }

    @Test
    fun `GET fuzzy rules returns list`() {
        whenever(fuzzyService.getRules()).thenReturn(
            listOf(FuzzyRuleEntity(domain = FuzzyDomain.GENERAL))
        )

        mockMvc.perform(get("/api/admin/fuzzy/rules"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].domain").value("GENERAL"))
    }

    @Test
    fun `GET users returns page`() {
        val user = com.misterio.curakid.core.database.auth.User(
            email = "test@test.com",
            passwordHash = "hash",
            name = "Test",
            role = com.misterio.curakid.core.database.auth.Role.ROLE_USER
        ).apply { id = UUID.randomUUID() }
        whenever(userService.getUsers(anyOrNull(), any(), any())).thenReturn(PageImpl(listOf(user)))

        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].fullName").value("Test"))
    }

    @Test
    fun `DELETE user returns 204`() {
        mockMvc.perform(delete("/api/admin/users/${UUID.randomUUID()}"))
            .andExpect(status().isNoContent())
    }

    @Test
    fun `GET vaccines returns list`() {
        whenever(vaccineService.getTemplates()).thenReturn(emptyList())

        mockMvc.perform(get("/api/admin/vaccines"))
            .andExpect(status().isOk)
    }

    @Test
    fun `POST vaccine creates template`() {
        whenever(vaccineService.createTemplate(any())).thenReturn(
            AdminVaccineTemplateResponse(UUID.randomUUID(), "BCG", "TB", 1, 1, "VACCINATION", 0, 3, null)
        )

        mockMvc.perform(
            post("/api/admin/vaccines")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"BCG","disease":"TB","doseNumber":1,"totalDoses":1,"type":"VACCINATION","scheduledAgeMonths":0,"scheduledAgeDays":3}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("BCG"))
    }

    @Test
    fun `DELETE vaccine returns 204`() {
        mockMvc.perform(delete("/api/admin/vaccines/${UUID.randomUUID()}"))
            .andExpect(status().isNoContent())
    }

    @Test
    fun `GET legal documents returns list`() {
        whenever(legalService.getAllDocuments()).thenReturn(emptyList())

        mockMvc.perform(get("/api/admin/legal"))
            .andExpect(status().isOk)
    }
}
