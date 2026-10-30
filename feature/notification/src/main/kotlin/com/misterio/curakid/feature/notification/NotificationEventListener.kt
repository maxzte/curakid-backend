package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.essential.common.events.AiReportGeneratedEvent
import com.misterio.curakid.core.essential.common.events.CatalogUpdatedEvent
import com.misterio.curakid.core.essential.common.events.NotificationPayloadConstants
import com.misterio.curakid.core.essential.logger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NotificationEventListener(
    private val notificationProducer: NotificationProducer,
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

        val notificationEvent = NotificationEvent(
            userId = event.userId,
            title = title,
            body = body,
            data = mapOf(
                NotificationPayloadConstants.KEY_TYPE to NotificationPayloadConstants.TYPE_AI_REPORT_READY,
                NotificationPayloadConstants.KEY_REPORT_ID to event.reportId.toString(),
                NotificationPayloadConstants.KEY_CHILD_ID to event.childId.toString()
            ),
            isUrgent = event.isEmergency
        )

        notificationProducer.enqueue(notificationEvent)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun handleCatalogUpdated(event: CatalogUpdatedEvent) {
        log.info("Handling CatalogUpdatedEvent for catalog: {}", event.catalogType)
        pushNotificationService.broadcastSilentSync(
            mapOf(
                NotificationPayloadConstants.KEY_TYPE to NotificationPayloadConstants.TYPE_DATA_SYNC,
                "catalog" to event.catalogType
            )
        )
    }
}
