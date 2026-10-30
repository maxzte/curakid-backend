package com.misterio.curakid.feature.chat

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.chat.ChatMessageRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.essential.STYLE_SECTION
import com.misterio.curakid.core.essential.appendAnthropometry
import com.misterio.curakid.core.essential.appendNorms
import com.misterio.curakid.core.essential.appendSection
import com.misterio.curakid.core.essential.sanitizeUserInput
import com.misterio.curakid.core.medical.ChartType
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.task.TaskExecutor
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.essential.common.PagedResponse
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant
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
    private val transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(ChatService::class.java)

    companion object {
        private const val SESSION_LIFETIME_HOURS = 24L
    }

    @Cacheable(value = ["chatHistory"], key = "#childId")
    fun getChatHistory(childId: UUID, currentUser: User): List<ChatMessageResponse> {
        val messages = requireNotNull(transactionManager.readTx {
            val child = childRepository.findById(childId)
                .orElseThrow { NoSuchElementException("Child not found") }

            if (child.user?.id != currentUser.id) {
                throw AccessDeniedException("Access denied")
            }

            val sessionStart = Instant.now().minus(SESSION_LIFETIME_HOURS, ChronoUnit.HOURS)
            chatMessageRepository.findAllByChildAndCreatedAtAfterOrderByCreatedAtAsc(child, sessionStart)
        })

        return messages.map {
            ChatMessageResponse(
                id = it.getId(),
                childId = it.child?.getId() ?: childId,
                userText = it.userText,
                aiText = it.aiText,
                createdAt = it.createdAt ?: Instant.now()
            )
        }
    }

    fun getChatHistory(childId: UUID, currentUser: User, pageable: Pageable): PagedResponse<ChatMessageResponse> {
        return transactionManager.readTx {
            val child = childRepository.findById(childId)
                .orElseThrow { NoSuchElementException("Child not found") }

            if (child.user?.id != currentUser.id) {
                throw AccessDeniedException("Access denied")
            }

            val page = chatMessageRepository.findAllByChildOrderByCreatedAtDesc(child, pageable)
                .map {
                    ChatMessageResponse(
                        id = it.getId(),
                        childId = it.child?.getId() ?: childId,
                        userText = it.userText,
                        aiText = it.aiText,
                        createdAt = it.createdAt ?: Instant.now()
                    )
                }

            PagedResponse(
                content = page.content,
                pageNumber = page.number,
                pageSize = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                last = page.isLast
            )
        }
    }

    fun streamChatEvents(request: ChatRequest, currentUser: User): SseEmitter {
        val (child, recentHistory) = requireNotNull(transactionManager.readTx {
            val c = childRepository.findById(request.childId!!)
                .orElseThrow { NoSuchElementException("Child not found") }

            if (c.user?.id != currentUser.id) {
                throw AccessDeniedException("Access denied")
            }

            val sessionStart = Instant.now().minus(SESSION_LIFETIME_HOURS, ChronoUnit.HOURS)
            val history = chatMessageRepository.findAllByChildAndCreatedAtAfterOrderByCreatedAtAsc(c, sessionStart)
                .takeLast(6)
            Pair(c, history)
        })

        val systemPrompt = buildSystemPrompt(child)
        val userMessage = request.text ?: ""

        val cleanUserMessage = sanitizeUserInput(userMessage)

        val fullUserPrompt = if (recentHistory.isNotEmpty()) {
            buildString {
                appendLine("# ПОПЕРЕДНІЙ ДІАЛОГ СЕСІЇ:")
                recentHistory.forEach { msg ->
                    appendLine("Батьки: ${sanitizeUserInput(msg.userText)}")
                    appendLine("Педіатр (ти): ${msg.aiText}")
                }
                appendLine()
                appendLine("# ПОТОЧНЕ ЗАПИТАННЯ БАТЬКІВ:")
                appendLine("<user_query>")
                appendLine(cleanUserMessage)
                appendLine("</user_query>")
            }
        } else {
            "<user_query>\n$cleanUserMessage\n</user_query>"
        }

        val emitter = SseEmitter(60_000L)
        val responseBuilder = StringBuilder()
        var disposable: reactor.core.Disposable? = null

        emitter.onCompletion { disposable?.dispose() }
        emitter.onTimeout {
            disposable?.dispose()
            emitter.complete()
        }
        emitter.onError { disposable?.dispose() }

        disposable = chatClient.prompt()
            .system(systemPrompt)
            .user(fullUserPrompt)
            .stream()
            .content()
            .retryWhen(
                Retry.backoff(3, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(5))
                    .filter { it is RuntimeException && it.message?.contains("503") == true }
            )
            .onErrorContinue { ex, _ ->
                if (ex is NoSuchElementException && ex.message?.contains("empty chunk") == true) {
                    log.debug("Skipping empty chunk")
                } else {
                    log.warn("Skipping chunk that caused error: ${ex.message}")
                }
            }
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
                    chatMessageService.save(
                        child,
                        userMessage,
                        responseBuilder.toString(),
                        request.id
                    )
                }
                emitter.complete()
            }
            .doOnError { ex ->
                log.error("Error during streaming", ex)
                taskExecutor.execute {
                    chatMessageService.save(
                        child,
                        userMessage,
                        "$responseBuilder\n[Помилка генерації]",
                        request.id
                    )
                }
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data("[Помилка генерації відповіді]")
                    )
                } catch (e: Exception) {
                    log.error("Failed to send error event", e)
                }
                emitter.complete()
            }
            .subscribe({}, { ex ->
                log.error("Unhandled error in stream subscriber", ex)
            })

        return emitter
    }

    private fun buildNorms(
        child: Child,
        ageDays: Int,
        latestMeasurement: Measurement?
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
            val ageDays = ChronoUnit.DAYS.between(child.birthDate, LocalDate.now()).toInt()
            percentileCalculator.getParamsForHeight(child.gender, height.toDouble(), ageDays)?.let { p ->
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
        val remainingDays = ChronoUnit.DAYS.between(birthDate.plusMonths(totalMonths), today)

        val ageMonths = totalMonths.toInt()
        val ageDaysNum = remainingDays.toInt()
        val totalAgeDays = (ageMonths * 30.44 + ageDaysNum).toInt()

        val latestMeasurement = transactionManager.readTx {
            measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child).orElse(null)
        }

        val norms = buildNorms(child, totalAgeDays, latestMeasurement)

        return buildString {
            appendLine(
                value = """
        # ROLE
        Ти — висококваліфікований педіатр та експерт із раннього розвитку дитини.
        Твоя мета: скласти персоналізований, заспокійливий, але професійний звіт для батьків.
        Не став діагнозів.
        Не вигадуй інформацію.
        """.trimIndent()
            )

            appendSection(
                title = "HARD RULES",
                items = listOf(
                    "Не повторюй дані з контексту без аналізу.",
                    "Не пояснюй FIS або внутрішні метрики.",
                    "Не згадуй нормальні показники без причини.",
                    "Кожне речення має додавати нову інформацію.",
                    "Максимум 4 речення у summary.",
                    "Уникай загальних фраз."
                )
            )

            appendLine(
                value = """
        # CONTEXT & DATA
        Вік дитини: $ageMonths міс. та $ageDaysNum дн.
        Стать: ${child.gender}
        """.trimIndent()
            )

            appendSection(
                title = "МЕДИЧНИЙ АНАМНЕЗ",
                items = listOf(
                    "Передчасне народження: ${if (child.isPremature) "ТАК" else "Ні"}",
                    "Алергії: ${child.allergies}",
                    "Хронічні захворювання: ${child.chronicConditions}",
                    "Перенесені хвороби: ${child.pastIllnesses}",
                    "Додаткові особливості: ${child.notes}"
                ),
                footer = "*(Важливо: якщо дитина народилася передчасно, зважай на це при оцінці моторних навичок)*"
            )

            appendAnthropometry(
                wfaZscore = latestMeasurement?.wfaZscore,
                hfaZscore = latestMeasurement?.hfaZscore,
                bmiZscore = latestMeasurement?.bmiZscore,
                hcfaZscore = latestMeasurement?.hcfaZscore,
                wfhZscore = latestMeasurement?.wfhZscore,
            )

            appendNorms(norms)

            appendLine(STYLE_SECTION)
        }
    }
}
