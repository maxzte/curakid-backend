package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.essential.common.events.AiReportGeneratedEvent
import com.misterio.curakid.core.essential.common.events.NotificationPayloadConstants
import com.misterio.curakid.core.essential.logger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class NotificationEventListener(
    private val pushNotificationService: PushNotificationService
) {

    private val log = logger()

    @EventListener
    fun handleAiReportGenerated(event: AiReportGeneratedEvent) {
        log.info("Handling AiReportGeneratedEvent for report: {}, child: {}", event.reportId, event.childId)

        val title = if (event.isEmergency) {
            "Увага! Терміновий аналіз стану дитини: ${event.childName}"
        } else {
            "AI-аналіз розвитку готовий: ${event.childName}"
        }

        val body = if (event.isEmergency) {
            "Зафіксовано суттєві відхилення показників. Перегляньте рекомендації лікаря."
        } else {
            "Новий детальний звіт про розвиток дитини успішно сформовано."
        }

        val payload = PushNotificationDto(
            title = title,
            body = body,
            data = mapOf(
                NotificationPayloadConstants.KEY_TYPE to NotificationPayloadConstants.TYPE_AI_REPORT_READY,
                NotificationPayloadConstants.KEY_REPORT_ID to event.reportId.toString(),
                NotificationPayloadConstants.KEY_CHILD_ID to event.childId.toString()
            )
        )

        pushNotificationService.sendNotificationToUser(event.userId, payload)
    }
}
