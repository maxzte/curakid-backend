package com.misterio.curakid.core.essential.common.events

import java.util.UUID

data class AiReportGeneratedEvent(
    val userId: UUID,
    val childId: UUID,
    val childName: String,
    val reportId: UUID,
    val isEmergency: Boolean = false
)

object NotificationPayloadConstants {
    const val KEY_TYPE = "type"
    const val KEY_CHILD_ID = "child_id"
    const val KEY_EVENT_ID = "extra_event_id"
    const val KEY_REPORT_ID = "extra_report_id"

    const val TYPE_EVENT_REMINDER = "EVENT_REMINDER"
    const val TYPE_AI_REPORT_READY = "AI_REPORT_READY"
}
