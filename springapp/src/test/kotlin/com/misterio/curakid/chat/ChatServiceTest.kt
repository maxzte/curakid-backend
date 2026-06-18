package com.misterio.curakid.chat

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import com.misterio.curakid.feature.chat.ChatMessageService
import com.misterio.curakid.feature.chat.ChatRequest
import com.misterio.curakid.feature.chat.ChatService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.contains
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.task.TaskExecutor
import reactor.core.publisher.Flux
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class ChatServiceTest {

    @Mock lateinit var chatClient: ChatClient
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var measurementRepository: MeasurementRepository
    @Mock lateinit var percentileCalculator: WhoPercentileCalculator
    @Mock lateinit var chatMessageRepository: ChatMessageRepository
    @Mock lateinit var chatMessageService: ChatMessageService
    @Mock lateinit var taskExecutor: TaskExecutor

    @InjectMocks
    lateinit var chatService: ChatService

    // --- Helpers ---

    private fun makeUser(id: UUID = UUID.randomUUID()) = User().apply { this.id = id }

    private fun makeChild(user: User, gender: Gender = Gender.FEMALE) =
        Child(user = user, birthDate = LocalDate.of(2025, 1, 1), gender = gender)
            .apply { id = UUID.randomUUID() }

    private fun mockChatClientChain(chunks: Flux<String>) {
        val requestSpec = mock<ChatClient.ChatClientRequestSpec>()
        val streamSpec = mock<ChatClient.StreamResponseSpec>()

        whenever(chatClient.prompt()).thenReturn(requestSpec)
        whenever(requestSpec.system(anyString())).thenReturn(requestSpec)
        whenever(requestSpec.user(anyString())).thenReturn(requestSpec)
        whenever(requestSpec.stream()).thenReturn(streamSpec)
        whenever(streamSpec.content()).thenReturn(chunks)
    }

    private fun mockTaskExecutorSync(latch: CountDownLatch) {
        doAnswer { invocation ->
            (invocation.getArgument<Runnable>(0)).run()
            latch.countDown()
            null
        }.whenever(taskExecutor).execute(any())
    }

    // --- getChatHistory ---

    @Test
    fun `getChatHistory - success when child exists and user has access`() {
        val userId = UUID.randomUUID()
        val currentUser = makeUser(userId)
        val child = makeChild(user = makeUser(userId))

        val chatMessage = ChatMessage(
            child = child,
            userText = "Питання",
            aiText = "Відповідь",
        ).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now()
        }

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(chatMessageRepository.findAllByChildOrderByCreatedAtAsc(child))
            .thenReturn(listOf(chatMessage))

        val result = chatService.getChatHistory(child.id!!, currentUser)

        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(chatMessage.id, result[0].id)
        assertEquals(chatMessage.userText, result[0].userText)
        assertEquals(chatMessage.aiText, result[0].aiText)
    }

    @Test
    fun `getChatHistory - filters messages older than 24 hours`() {
        val userId = UUID.randomUUID()
        val currentUser = makeUser(userId)
        val child = makeChild(user = makeUser(userId))

        val oldMessage = ChatMessage(
            child = child,
            userText = "Старе",
            aiText = "Відповідь",
        ).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now().minus(25, ChronoUnit.HOURS)
        }

        val newMessage = ChatMessage(
            child = child,
            userText = "Нове",
            aiText = "Відповідь",
        ).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now()
        }

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(chatMessageRepository.findAllByChildOrderByCreatedAtAsc(child))
            .thenReturn(listOf(oldMessage, newMessage))

        val result = chatService.getChatHistory(child.id!!, currentUser)

        assertEquals(1, result.size)
        assertEquals(newMessage.id, result[0].id)
        assertEquals("Нове", result[0].userText)
    }

    @Test
    fun `getChatHistory - throws NoSuchElementException when child not found`() {
        val childId = UUID.randomUUID()
        val currentUser = makeUser()

        whenever(childRepository.findById(childId)).thenReturn(Optional.empty())

        assertThrows(NoSuchElementException::class.java) {
            chatService.getChatHistory(childId, currentUser)
        }
    }

    @Test
    fun `getChatHistory - throws IllegalArgumentException when access denied`() {
        val currentUser = makeUser()
        val child = makeChild(user = makeUser())

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))

        assertThrows(IllegalArgumentException::class.java) {
            chatService.getChatHistory(child.id!!, currentUser)
        }
    }

    // --- streamChatEvents ---

    @Test
    fun `streamChatEvents - success flow with streaming`() {
        val userId = UUID.randomUUID()
        val currentUser = makeUser(userId)
        val child = makeChild(user = makeUser(userId))
        val request = ChatRequest(id = UUID.randomUUID(), childId = child.id, text = "У дитини зубки ріжуться")

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child))
            .thenReturn(Optional.empty())
        mockChatClientChain(Flux.just("Перша ", "частина ", "відповіді."))

        val latch = CountDownLatch(1)
        mockTaskExecutorSync(latch)

        val emitter = chatService.streamChatEvents(request, currentUser)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Streaming did not complete in time")
        assertNotNull(emitter)
        verify(chatMessageService).save(
            eq(child),
            eq("У дитини зубки ріжуться"),
            eq("Перша частина відповіді."),
            eq(request.id)
        )
    }

    @Test
    fun `streamChatEvents - throws NoSuchElementException when child not found`() {
        val childId = UUID.randomUUID()
        val currentUser = makeUser()
        val request = ChatRequest(id = UUID.randomUUID(), childId = childId, text = "Текст")

        whenever(childRepository.findById(childId)).thenReturn(Optional.empty())

        assertThrows(NoSuchElementException::class.java) {
            chatService.streamChatEvents(request, currentUser)
        }
    }

    @Test
    fun `streamChatEvents - throws IllegalArgumentException when access denied`() {
        val currentUser = makeUser()
        val child = makeChild(user = makeUser())
        val request = ChatRequest(id = UUID.randomUUID(), childId = child.id, text = "Текст")

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))

        assertThrows(IllegalArgumentException::class.java) {
            chatService.streamChatEvents(request, currentUser)
        }
    }

    @Test
    fun `streamChatEvents - handles AI error and saves with error marker`() {
        val userId = UUID.randomUUID()
        val currentUser = makeUser(userId)
        val child = makeChild(user = makeUser(userId))
        val request = ChatRequest(id = UUID.randomUUID(), childId = child.id, text = "Хелп")

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child))
            .thenReturn(Optional.empty())
        mockChatClientChain(Flux.error(RuntimeException("AI Server down")))

        val latch = CountDownLatch(1)
        mockTaskExecutorSync(latch)

        val emitter = chatService.streamChatEvents(request, currentUser)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Error handling did not complete in time")
        assertNotNull(emitter)
        verify(chatMessageService).save(
            eq(child),
            eq("Хелп"),
            contains("[Помилка генерації]"),
            eq(request.id)
        )
    }

    @Test
    fun `streamChatEvents - skips empty NoSuchElement chunks via onErrorContinue`() {
        val userId = UUID.randomUUID()
        val currentUser = makeUser(userId)
        val child = makeChild(user = makeUser(userId))
        val request = ChatRequest(id = UUID.randomUUID(), childId = child.id, text = "Тест")

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child))
            .thenReturn(Optional.empty())

        val mixedFlux = Flux.concat(
            Flux.just("Привіт "),
            Flux.error(NoSuchElementException("empty chunk")),
            Flux.just("світ!")
        )
        mockChatClientChain(mixedFlux)

        val latch = CountDownLatch(1)
        mockTaskExecutorSync(latch)

        val emitter = chatService.streamChatEvents(request, currentUser)
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        verify(chatMessageService).save(
            eq(child),
            eq("Тест"),
            contains("Привіт"),
            eq(request.id)
        )
    }

    @Test
    fun `streamChatEvents - handles partial response before AI error`() {
        val userId = UUID.randomUUID()
        val currentUser = makeUser(userId)
        val child = makeChild(user = makeUser(userId))
        val request = ChatRequest(id = UUID.randomUUID(), childId = child.id, text = "Питання")

        whenever(childRepository.findById(child.id!!)).thenReturn(Optional.of(child))
        whenever(measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child))
            .thenReturn(Optional.empty())

        val partialThenError = Flux.concat(
            Flux.just("Часткова ", "відповідь "),
            Flux.error(RuntimeException("Connection lost"))
        )
        mockChatClientChain(partialThenError)

        val latch = CountDownLatch(1)
        mockTaskExecutorSync(latch)

        val emitter = chatService.streamChatEvents(request, currentUser)

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Partial error handling did not complete in time")
        assertNotNull(emitter)
        verify(chatMessageService).save(
            eq(child),
            eq("Питання"),
            eq("Часткова відповідь \n[Помилка генерації]"),
            eq(request.id)
        )
    }
}