package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.essential.common.events.NotificationPayloadConstants
import com.misterio.curakid.core.essential.logger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class EventReminderScheduler(
    private val eventRepository: EventRepository,
    private val pushNotificationService: PushNotificationService,
    private val redisTemplate: StringRedisTemplate? = null
) {

    private val log = logger()

    companion object {
        private const val LOCK_KEY = "lock:scheduler:event-reminders"
        private val LOCK_DURATION = Duration.ofMinutes(15)
    }

    @Scheduled(fixedDelayString = "\${notification.scheduler.interval-ms:3600000}")
    @Transactional
    fun sendUpcomingEventReminders() {
        if (redisTemplate != null) {
            val acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_DURATION)
            if (acquired != true) {
                log.debug("Another cluster node is running event reminder scheduler. Skipping.")
                return
            }
        }

        val now = Instant.now()
        val windowEnd = now.plus(24, ChronoUnit.HOURS)

        val pendingEvents = eventRepository.findPendingReminders(now, windowEnd)
        if (pendingEvents.isEmpty()) {
            return
        }

        log.info("Found {} pending events requiring reminder in next 24h", pendingEvents.size)

        for (event in pendingEvents) {
            val child = event.child ?: continue
            val user = child.user ?: continue

            val payload = PushNotificationDto(
                title = "Нагадування: ${child.name}",
                body = "Заплановано подію: ${event.title}",
                data = mapOf(
                    NotificationPayloadConstants.KEY_TYPE to NotificationPayloadConstants.TYPE_EVENT_REMINDER,
                    NotificationPayloadConstants.KEY_EVENT_ID to event.id.toString(),
                    NotificationPayloadConstants.KEY_CHILD_ID to child.id.toString()
                )
            )

            try {
                pushNotificationService.sendNotificationToUser(user.id, payload)
                event.reminderSent = true
                eventRepository.save(event)
                log.info("Reminder sent and event {} marked as reminderSent=true", event.id)
            } catch (e: Exception) {
                log.error("Failed to send reminder for event {}: {}", event.id, e.message, e)
            }
        }
    }
}
