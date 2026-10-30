package com.misterio.curakid.core.essential.common.events

import com.misterio.curakid.core.essential.domain.ChildId
import com.misterio.curakid.core.essential.domain.ReportId
import com.misterio.curakid.core.essential.domain.UserId
import java.util.UUID

data class AiReportGeneratedEvent(
    val userId: UUID,
    val childId: UUID,
    val childName: String,
    val reportId: UUID,
    val isEmergency: Boolean = false
) {
    val typedUserId: UserId get() = UserId(userId)
    val typedChildId: ChildId get() = ChildId(childId)
    val typedReportId: ReportId get() = ReportId(reportId)

    companion object {
        fun from(
            userId: UserId,
            childId: ChildId,
            childName: String,
            reportId: ReportId,
            isEmergency: Boolean = false
        ): AiReportGeneratedEvent = AiReportGeneratedEvent(
            userId = userId.value,
            childId = childId.value,
            childName = childName,
            reportId = reportId.value,
            isEmergency = isEmergency
        )
    }
}

object NotificationPayloadConstants {
    const val KEY_TYPE = "type"
    const val KEY_CHILD_ID = "child_id"
    const val KEY_EVENT_ID = "extra_event_id"
    const val KEY_REPORT_ID = "extra_report_id"

    const val TYPE_EVENT_REMINDER = "EVENT_REMINDER"
    const val TYPE_AI_REPORT_READY = "AI_REPORT_READY"
    const val TYPE_DATA_SYNC = "DATA_SYNC"
}

data class CatalogUpdatedEvent(
    val catalogType: String
)
