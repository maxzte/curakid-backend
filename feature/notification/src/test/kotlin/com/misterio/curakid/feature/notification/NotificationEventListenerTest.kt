package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.essential.common.events.AiReportGeneratedEvent
import com.misterio.curakid.core.essential.common.events.NotificationPayloadConstants
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class NotificationEventListenerTest {

    private lateinit var pushNotificationService: PushNotificationService
    private lateinit var listener: NotificationEventListener

    @BeforeEach
    fun setUp() {
        pushNotificationService = mock()
        listener = NotificationEventListener(pushNotificationService)
    }

    @Test
    fun `handleAiReportGenerated sends push notification with reportId and childId in payload`() {
        val userId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val reportId = UUID.randomUUID()
        val event = AiReportGeneratedEvent(
            userId = userId,
            childId = childId,
            childName = "Марко",
            reportId = reportId,
            isEmergency = false
        )

        listener.handleAiReportGenerated(event)

        verify(pushNotificationService).sendNotificationToUser(
            eq(userId),
            argThat {
                this.title.contains("Марко") &&
                this.data[NotificationPayloadConstants.KEY_TYPE] == NotificationPayloadConstants.TYPE_AI_REPORT_READY &&
                this.data[NotificationPayloadConstants.KEY_REPORT_ID] == reportId.toString() &&
                this.data[NotificationPayloadConstants.KEY_CHILD_ID] == childId.toString()
            }
        )
    }

    @Test
    fun `handleAiReportGenerated sends high priority title for emergency report`() {
        val userId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        val reportId = UUID.randomUUID()
        val event = AiReportGeneratedEvent(
            userId = userId,
            childId = childId,
            childName = "Марко",
            reportId = reportId,
            isEmergency = true
        )

        listener.handleAiReportGenerated(event)

        verify(pushNotificationService).sendNotificationToUser(
            eq(userId),
            argThat {
                this.title.contains("Увага") || this.title.contains("Терміново")
            }
        )
    }
}
