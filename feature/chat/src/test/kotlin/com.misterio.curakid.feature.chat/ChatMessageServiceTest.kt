package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ChatMessageServiceTest {

    @Mock
    private lateinit var chatMessageRepository: ChatMessageRepository

    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var chatMessageService: ChatMessageService

    private val user = User(
        email = "parent@example.com",
        name = "Parent User"
    ).apply { id = UUID.randomUUID() }

    private val child = Child(
        user = user,
        name = "Sofia",
        birthDate = LocalDate.of(2023, 5, 10),
        gender = Gender.FEMALE
    ).apply { id = UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        chatMessageService = ChatMessageService(chatMessageRepository, transactionManager)
    }

    @Test
    fun `save should persist chat message when message id does not exist`() {
        // Given
        val messageId = UUID.randomUUID()
        whenever(chatMessageRepository.findById(messageId)).thenReturn(Optional.empty())

        // When
        chatMessageService.save(
            child = child,
            userText = "What vaccines are due?",
            aiText = "Your child is due for MMR.",
            id = messageId,
            urgencyLevel = "ROUTINE",
            verifiedSources = listOf("CDC", "WHO")
        )

        // Then
        val captor = argumentCaptor<ChatMessage>()
        verify(chatMessageRepository).save(captor.capture())
        val saved = captor.firstValue

        assertEquals(messageId, saved.id)
        assertEquals(child, saved.child)
        assertEquals("What vaccines are due?", saved.userText)
        assertEquals("Your child is due for MMR.", saved.aiText)
        assertEquals("ROUTINE", saved.urgencyLevel)
        assertEquals(listOf("CDC", "WHO"), saved.verifiedSources)
    }

    @Test
    fun `save should skip persistence when message id already exists`() {
        // Given
        val messageId = UUID.randomUUID()
        val existingMessage = ChatMessage(
            child = child,
            userText = "Old question",
            aiText = "Old answer"
        ).apply { setId(messageId) }
        whenever(chatMessageRepository.findById(messageId)).thenReturn(Optional.of(existingMessage))

        // When
        chatMessageService.save(
            child = child,
            userText = "Duplicate message",
            aiText = "Duplicate answer",
            id = messageId
        )

        // Then
        verify(chatMessageRepository, never()).save(any())
    }
}
