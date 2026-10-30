package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.essential.common.events.NotificationPayloadConstants
import com.misterio.curakid.core.essential.logger
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.writeTx
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class EventReminderScheduler(
    private val eventRepository: EventRepository,
    private val notificationProducer: NotificationProducer,
    private val redisTemplate: StringRedisTemplate? = null,
    private val transactionManager: PlatformTransactionManager? = null
) {

    private val log = logger()

    companion object {
        private const val LOCK_KEY = "lock:scheduler:event-reminders"
        private val LOCK_DURATION = Duration.ofMinutes(15)
        private const val BATCH_SIZE = 500
        private const val MAX_BATCHES_PER_RUN = 50 // Guardrail: up to 25,000 events per schedule run
    }

    @Scheduled(fixedDelayString = $$"${notification.scheduler.interval-ms:3600000}")
    fun sendUpcomingEventReminders() {
        if (redisTemplate != null) {
            val acquired = try {
                redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_DURATION)
            } catch (e: Exception) {
                log.warn("Redis unavailable for scheduler lock: {}", e.message)
                true
            }
            if (acquired != true) {
                log.debug("Another cluster node is running event reminder scheduler. Skipping.")
                return
            }
        }

        val now = Instant.now()
        val windowEnd = now.plus(24, ChronoUnit.HOURS)

        var batchIndex = 0
        var totalProcessed = 0

        while (batchIndex < MAX_BATCHES_PER_RUN) {
            val pendingBatch = eventRepository.findPendingReminders(
                from = now,
                to = windowEnd,
                pageable = PageRequest.of(0, BATCH_SIZE)
            )

            if (pendingBatch.isEmpty()) {
                break
            }

            log.info("Processing event reminder batch {}: {} events", batchIndex + 1, pendingBatch.size)
            val successfullyEnqueuedIds = mutableListOf<UUID>()

            for (event in pendingBatch) {
                val child = event.child ?: continue
                val user = child.user ?: continue

                val notificationEvent = NotificationEvent(
                    userId = user.id,
                    title = "Нагадування: ${child.name}",
                    body = "Заплановано подію: ${event.title}",
                    data = mapOf(
                        NotificationPayloadConstants.KEY_TYPE to NotificationPayloadConstants.TYPE_EVENT_REMINDER,
                        NotificationPayloadConstants.KEY_EVENT_ID to event.id.toString(),
                        NotificationPayloadConstants.KEY_CHILD_ID to child.id.toString()
                    ),
                    isUrgent = true
                )

                try {
                    notificationProducer.enqueue(notificationEvent)
                    successfullyEnqueuedIds.add(event.id)
                } catch (e: Exception) {
                    log.error("Failed to enqueue reminder for event {}: {}", event.id, e.message, e)
                }
            }

            // Bulk update reminder_sent flag in a short transaction
            if (successfullyEnqueuedIds.isNotEmpty()) {
                val updateAction = {
                    eventRepository.markRemindersSent(successfullyEnqueuedIds)
                }
                if (transactionManager != null) {
                    transactionManager.writeTx { updateAction() }
                } else {
                    updateAction()
                }
                totalProcessed += successfullyEnqueuedIds.size
            }

            batchIndex++
            if (pendingBatch.size < BATCH_SIZE) {
                break
            }
        }

        if (totalProcessed > 0) {
            log.info("Completed event reminder scheduler run: processed {} events across {} batches", totalProcessed, batchIndex)
        }
    }
}
