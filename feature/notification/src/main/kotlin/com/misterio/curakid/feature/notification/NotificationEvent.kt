package com.misterio.curakid.feature.notification

import java.time.Instant
import java.util.UUID

data class NotificationEvent(
    val eventId: UUID = UUID.randomUUID(),
    val userId: UUID,
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
    val isUrgent: Boolean = false,
    val createdAt: Instant = Instant.now()
) {
    // Default constructor for Jackson deserialization
    constructor() : this(
        eventId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        title = "",
        body = "",
        data = emptyMap(),
        isUrgent = false,
        createdAt = Instant.now()
    )
}

object NotificationTopics {
    const val NOTIFICATIONS = "curakid.notifications"
    const val NOTIFICATIONS_DLQ = "curakid.notifications.DLQ"
}
