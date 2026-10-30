package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.feature.auth.controller.LegalDocumentController
import com.misterio.curakid.feature.auth.service.LegalDocumentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class LegalDocumentControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var service: LegalDocumentService

    @BeforeEach
    fun setUp() {
        service = mock()
        val controller = LegalDocumentController(service)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET api legal returns active documents`() {
        whenever(service.getActiveDocuments()).thenReturn(
            listOf(LegalDocumentResponse(type = "TERMS", version = "1.0", url = "/terms/v1", publishedAt = Instant.now()))
        )

        mockMvc.perform(get("/api/v1/legal"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("TERMS"))
            .andExpect(jsonPath("$[0].version").value("1.0"))
    }

    @Test
    fun `GET api legal by type returns document`() {
        whenever(service.getActiveDocument(ConsentType.TERMS)).thenReturn(
            LegalDocumentResponse(type = "TERMS", version = "1.0", url = "/terms/v1", publishedAt = Instant.now())
        )

        mockMvc.perform(get("/api/v1/legal/TERMS"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("TERMS"))
    }
}
