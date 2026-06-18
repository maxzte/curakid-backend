package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.feature.chat.ChatController
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class ChatControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var chatService: ChatService
    private val user = User(email = "test@test.com", passwordHash = "hash", name = "Test").apply {
        id = UUID.randomUUID()
    }
    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())
    private val childId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        chatService = mock()
        val controller = ChatController(chatService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET chat history returns list`() {
        val messageId = UUID.randomUUID()
        whenever(chatService.getChatHistory(childId, user)).thenReturn(
            listOf(ChatMessageResponse(messageId, childId, "Hello", "Hi there", Instant.now()))
        )

        mockMvc.perform(get("/api/chat/history/$childId").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].userText").value("Hello"))
            .andExpect(jsonPath("$[0].aiText").value("Hi there"))
    }
}
