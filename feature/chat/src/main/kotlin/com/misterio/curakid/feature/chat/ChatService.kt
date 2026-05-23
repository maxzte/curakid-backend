package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.chat.ChatMessage
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.medical.ChartType
import com.misterio.curakid.core.medical.WhoPercentileCalculator
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
import java.util.UUID

@Service
class ChatService(
    private val chatClient: ChatClient,
    private val childRepository: ChildRepository,
    private val measurementRepository: MeasurementRepository,
    private val percentileCalculator: WhoPercentileCalculator,
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
                    chatMessageService.save(
                        child,
                        userMessage,
                        "$responseBuilder\n[Помилка генерації]"
                    )
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

    private fun buildNorms(
        child: Child,
        ageDays: Int,
        latestMeasurement: com.misterio.curakid.core.database.measurements.Measurement?
    ): Map<String, Map<String, Double>> {
        val res = mutableMapOf<String, Map<String, Double>>()

        fun addNorms(variable: String, type: ChartType) {
            percentileCalculator.getParamsForAge(type, child.gender, ageDays)?.let { p ->
                res[variable] = mapOf(
                    "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                )
            }
        }

        addNorms("wfaZscore", ChartType.WFA)
        addNorms("hfaZscore", ChartType.HFA)
        addNorms("bmiZscore", ChartType.BMI)
        addNorms("hcfaZscore", ChartType.HCFA)

        latestMeasurement?.heightCm?.let { height ->
            percentileCalculator.getParamsForHeight(child.gender, height.toDouble())?.let { p ->
                res["wfhZscore"] = mapOf(
                    "P3" to p.p3, "P15" to p.p15, "P50" to p.p50, "P85" to p.p85, "P97" to p.p97
                )
            }
        }

        return res
    }

    private fun buildSystemPrompt(child: Child): String {
        val today = LocalDate.now()
        val birthDate = child.birthDate
        val totalMonths = ChronoUnit.MONTHS.between(birthDate, today)
        val birthDatePlusMonths = birthDate.plusMonths(totalMonths)
        val remainingDays = ChronoUnit.DAYS.between(birthDatePlusMonths, today)

        val ageMonths = totalMonths.toInt()
        val ageDaysNum = remainingDays.toInt()
        val totalAgeDays = (ageMonths * 30.44 + ageDaysNum).toInt()

        val latestMeasurement =
            measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child).orElse(null)

        val norms = buildNorms(child, totalAgeDays, latestMeasurement)
        val normsContext = if (norms.isNotEmpty()) {
            val sections = norms.map { (variable, points) ->
                val pointsStr = points.entries.sortedBy {
                    it.key.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                }
                    .joinToString(", ") { "${it.key}=${it.value}" }
                "- $variable: $pointsStr"
            }.joinToString("\n")
            "\n## NORMATIVE DATA (WHO Standards for this age/height):\n$sections"
        } else ""

        val anthropometry = latestMeasurement?.let {
            """
            Вага (WFA): ${it.wfaZscore ?: "немає даних"}
            Зріст (HFA): ${it.hfaZscore ?: "немає даних"}
            Індекс маси тіла (BMI): ${it.bmiZscore ?: "немає даних"}
            Окружність голови (HCFA): ${it.hcfaZscore ?: "немає даних"}
            Вага-до-зросту (WFH): ${it.wfhZscore ?: "немає даних"}
            (Z-scores за стандартами ВООЗ: 0 — ідеал, від -2 до +2 — норма)
            """.trimIndent()
        } ?: "Дані антропометрії відсутні."

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
            if (child.pastIllnesses.isNotEmpty()) append(
                "- Перенесені хвороби: ${
                    child.pastIllnesses.joinToString(
                        ", "
                    )
                }\n"
            )
        }.ifEmpty { "Відсутній" }

        return """
            # РОЛЬ
            Ти — висококваліфікований педіатр-консультант у додатку CuraKid. Твоя мета — надавати батькам точну, науково обґрунтовану, але доступну інформацію про розвиток та здоров'я їхньої дитини.
            
            # КОНТЕКСТ ДИТИНИ:
            Вік: $totalMonths міс. та $remainingDays дн.
            Стать: ${child.gender.name}
            
            ## МЕДИЧНИЙ АНАМНЕЗ:
            $medicalHistory
            
            ## АНТРОПОМЕТРІЯ (Останні показники):
            $anthropometry
            $normsContext
            
            # ІНСТРУКЦІЇ:
            - Відповідай емпатично, професійно та зрозуміло. Уникай надмірної паніки, але будь серйозним у важливих питаннях.
            - Якщо запитання стосується встановлення діагнозів або призначення лікування, ОБОВ'ЯЗКОВО наголоси на необхідності очної консультації з педіатром.
            - Використовуй дані антропометрії для оцінки фізичного стану (порівнюй із нормами ВООЗ, де 0 — медіана).
            - Відповідай виключно українською мовою.
            - Використовуй Markdown для структурування відповіді (заголовки, списки).
        """.trimIndent()
    }
}
