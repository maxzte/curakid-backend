package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.core.publisher.Flux
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val childRepository: ChildRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatMessageService: ChatMessageService,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(ChatService::class.java)

    @Transactional(readOnly = true)
    fun getChatHistory(childId: UUID, currentUser: User): List<ChatMessageResponse> {
        val child = childRepository.findById(childId)
            .orElseThrow { NoSuchElementException("Child not found") }

        if (child.user?.id != currentUser.id) {
            throw IllegalArgumentException("Access denied")
        }

        return chatMessageRepository.findAllByChildOrderByCreatedAtAsc(child).map {
            ChatMessageResponse(
                id = it.id!!,
                childId = it.child!!.id!!,
                userText = it.userText,
                aiText = it.aiText,
                createdAt = it.createdAt ?: java.time.LocalDateTime.now()
            )
        }
    }

    fun streamChat(request: ChatRequest, currentUser: User): Flux<String> {
        val child = childRepository.findById(request.childId!!)
            .orElseThrow { NoSuchElementException("Child not found") }

        if (child.user?.id != currentUser.id) {
            throw IllegalArgumentException("Access denied")
        }

        val systemPrompt = buildSystemPrompt(child)
        val userMessage = request.text ?: ""

        val aiStream: Flux<String> = try {
            chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content()
        } catch (ex: Exception) {
            log.error("Failed to start AI stream", ex)
            Flux.just("Вибачте, сталася помилка при зверненні до ШІ. Спробуйте пізніше.")
        }

        // Ми хочемо зберегти повідомлення в БД після завершення генерації.
        // Зберемо всі чанки в одну стрінгу
        return aiStream
            .collectList()
            .map { chunks ->
                val fullResponse = chunks.joinToString("")
                saveMessage(child, userMessage, fullResponse)
            }
            .flatMapMany {
                // Повертаємо оригінальний стрім клієнту
                // Але так ми не зможемо стрімити по частинах, бо collectList чекає кінця.
                // Краще використовувати doOnComplete або щось подібне
                Flux.empty<String>() // placeholder, see fix below
            }
    }

    fun streamChatEvents(request: ChatRequest, currentUser: User): SseEmitter {
        val child = childRepository.findById(request.childId!!)
            .orElseThrow { NoSuchElementException("Child not found") }

        if (child.user?.id != currentUser.id) {
            throw IllegalArgumentException("Access denied")
        }

        val systemPrompt = buildSystemPrompt(child)
        val userMessage = request.text ?: ""
        val emitter = SseEmitter(60_000L)
        val responseBuilder = StringBuilder()

        chatClient.prompt()
            .system(systemPrompt)
            .user(userMessage)
            .stream()
            .content()
            .doOnNext { chunk ->
                responseBuilder.append(chunk)
                try {
                    emitter.send(chunk)
                } catch (e: Exception) {
                    log.error("Failed to send chunk", e)
                }
            }
            .doOnComplete {
                taskExecutor.execute {
                    chatMessageService.save(child, userMessage, responseBuilder.toString())
                }
                emitter.complete()
            }
            .doOnError { ex ->
                log.error("Error during streaming", ex)
                taskExecutor.execute {
                    chatMessageService.save(child, userMessage, "$responseBuilder\n[Помилка генерації]")
                }
                emitter.completeWithError(ex)
            }
            .subscribe()

        return emitter
    }

    private fun saveMessageAsync(child: Child, userMessage: String, aiResponse: String) {
        try {
            val message = ChatMessage(
                child = child,
                userText = userMessage,
                aiText = aiResponse
            )
            chatMessageRepository.save(message)
        } catch (e: Exception) {
            log.error("Failed to save chat message", e)
        }
    }

    private fun saveMessage(child: Child, userMessage: String, aiResponse: String) {
        val message = ChatMessage(
            child = child,
            userText = userMessage,
            aiText = aiResponse
        )
        chatMessageRepository.save(message)
    }

    private fun buildSystemPrompt(child: Child): String {
        val ageMonths = ChronoUnit.MONTHS.between(child.birthDate, LocalDate.now()).toInt()

        val medicalHistory = buildString {
            if (child.isPremature) append("- Передчасне народження\n")
            if (child.allergies.isNotEmpty()) append("- Алергії: ${child.allergies.joinToString(", ")}\n")
            if (child.chronicConditions.isNotEmpty()) append(
                "- Хронічні захворювання: ${
                    child.chronicConditions.joinToString(
                        ", "
                    )
                }\n"
            )
            if (child.pastIllnesses.isNotEmpty()) append("- Перенесені хвороби: ${child.pastIllnesses.joinToString(", ")}\n")
        }.ifEmpty { "Відсутній" }

        return """
            Ти — педіатр-консультант у додатку CuraKid. Твоя задача — відповідати на запитання батьків щодо їхньої дитини.
            
            # КОНТЕКСТ ДИТИНИ:
            Вік: $ageMonths міс.
            Стать: ${child.gender.name}
            Медичний анамнез:
            $medicalHistory
            
            # ІНСТРУКЦІЇ:
            - Відповідай емпатично, професійно та зрозуміло.
            - Якщо запитання стосується діагнозів, обов'язково порадь звернутися до лікаря.
            - Відповідай виключно українською мовою.
            - Формат відповіді: Markdown.
        """.trimIndent()
    }
}
