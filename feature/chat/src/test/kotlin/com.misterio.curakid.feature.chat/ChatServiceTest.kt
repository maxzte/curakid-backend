package com.misterio.curakid.feature.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.essential.common.analysis.ChatTriageResponseDto
import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.task.TaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock
    lateinit var chatClient: ChatClient
    @Mock
    lateinit var childRepository: ChildRepository
    @Mock
    lateinit var measurementRepository: MeasurementRepository
    @Mock
    lateinit var percentileCalculator: WhoPercentileCalculator
    @Mock
    lateinit var chatMessageRepository: ChatMessageRepository
    @Mock
    lateinit var chatMessageService: ChatMessageService
    @Mock
    lateinit var intelligenceProvider: IntelligenceProvider
    @Mock
    lateinit var knowledgeProvider: com.misterio.curakid.core.essential.common.analysis.KnowledgeProvider
    @Mock
    lateinit var objectMapper: ObjectMapper
    @Mock
    lateinit var taskExecutor: TaskExecutor
    @Mock
    lateinit var transactionManager: PlatformTransactionManager

    @InjectMocks
    lateinit var chatService: ChatService

    @BeforeEach
    fun setUp() {
        whenever(intelligenceProvider.triageChat(any())).thenReturn(
            ChatTriageResponseDto(
                intent = "SYMPTOM_OR_ILLNESS",
                urgencyLevel = "ROUTINE",
                needsRagSearch = false,
                asksForMedicationOrDiagnosis = false
            )
        )
        whenever(objectMapper.writeValueAsString(any())).thenReturn("{}")
    }

    private fun makeUser(id: UUID = UUID.randomUUID()) = User().apply { this.id = id }

    private fun makeChild(user: User, gender: Gender = Gender.FEMALE) =
        Child(user = user, name = "Sofia", birthDate = LocalDate.now().minusMonths(6), gender = gender).apply {
            id = UUID.randomUUID()
        }

    @Test
    fun `getChatHistory - throws NoSuchElementException when child not found`() {
        val user = makeUser()
        val childId = UUID.randomUUID()
        whenever(childRepository.findById(childId)).thenReturn(Optional.empty())

        Assertions.assertThrows(NoSuchElementException::class.java) {
            chatService.getChatHistory(childId, user)
        }
    }

    @Test
    fun `getChatHistory - throws AccessDeniedException when access denied`() {
        val owner = makeUser()
        val otherUser = makeUser()
        val child = makeChild(owner)

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))

        Assertions.assertThrows(org.springframework.security.access.AccessDeniedException::class.java) {
            chatService.getChatHistory(child.id!!, otherUser)
        }
    }

    @Test
    fun `getChatHistory - success when child exists and user has access`() {
        val user = makeUser()
        val child = makeChild(user)

        val msg1 = ChatMessage(child = child, userText = "Hi", aiText = "Hello").apply {
            setId(UUID.randomUUID())
            createdAt = Instant.now().minus(1, ChronoUnit.HOURS)
        }

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(chatMessageRepository.findAllByChildAndCreatedAtAfterOrderByCreatedAtAsc(eq(child), any()))
            .thenReturn(listOf(msg1))

        val result = chatService.getChatHistory(child.id!!, user)

        assertEquals(1, result.size)
        assertEquals("Hi", result[0].userText)
        assertEquals("Hello", result[0].aiText)
    }

    @Test
    fun `getChatHistory - filters messages older than 24 hours`() {
        val user = makeUser()
        val child = makeChild(user)

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(chatMessageRepository.findAllByChildAndCreatedAtAfterOrderByCreatedAtAsc(eq(child), any()))
            .thenReturn(emptyList())

        val result = chatService.getChatHistory(child.id!!, user)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `streamChatEvents - throws NoSuchElementException when child not found`() {
        val user = makeUser()
        val childId = UUID.randomUUID()
        val request = ChatRequest(id = UUID.randomUUID(), childId = childId, text = "Hello")

        whenever(childRepository.findById(childId)).thenReturn(Optional.empty())

        Assertions.assertThrows(NoSuchElementException::class.java) {
            chatService.streamChatEvents(request, user)
        }
    }

    @Test
    fun `streamChatEvents - throws AccessDeniedException when access denied`() {
        val owner = makeUser()
        val otherUser = makeUser()
        val child = makeChild(owner)
        val request = ChatRequest(id = UUID.randomUUID(), childId = child.id!!, text = "Hello")

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))

        Assertions.assertThrows(org.springframework.security.access.AccessDeniedException::class.java) {
            chatService.streamChatEvents(request, otherUser)
        }
    }
}
