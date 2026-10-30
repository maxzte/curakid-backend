package com.misterio.curakid.core.essential.domain

import com.misterio.curakid.core.essential.common.events.AiReportGeneratedEvent
import com.misterio.curakid.core.essential.common.events.ChildCreatedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

class DomainIdentifiersTest {

    @Test
    fun `value classes should wrap and format UUIDs deterministically`() {
        // Given
        val rawUserId = UUID.randomUUID()
        val rawChildId = UUID.randomUUID()
        val rawReportId = UUID.randomUUID()
        val rawSummaryId = UUID.randomUUID()
        val rawMeasurementId = UUID.randomUUID()
        val rawQuestionId = UUID.randomUUID()

        // When
        val userId = rawUserId.toUserId()
        val childId = rawChildId.toChildId()
        val reportId = rawReportId.toReportId()
        val summaryId = rawSummaryId.toPeriodSummaryId()
        val measurementId = rawMeasurementId.toMeasurementId()
        val questionId = rawQuestionId.toQuestionId()

        // Then
        assertEquals(rawUserId, userId.value)
        assertEquals(rawUserId.toString(), userId.toString())
        assertEquals(rawChildId, childId.value)
        assertEquals(rawReportId, reportId.value)
        assertEquals(rawSummaryId, summaryId.value)
        assertEquals(rawMeasurementId, measurementId.value)
        assertEquals(rawQuestionId, questionId.value)
    }

    @Test
    fun `domain events should support value class constructors and typed accessors`() {
        // Given
        val userId = UUID.randomUUID().toUserId()
        val childId = UUID.randomUUID().toChildId()
        val reportId = UUID.randomUUID().toReportId()

        // When
        val childEvent = ChildCreatedEvent.from(childId)
        val reportEvent = AiReportGeneratedEvent.from(
            userId = userId,
            childId = childId,
            childName = "Ivan",
            reportId = reportId,
            isEmergency = true
        )

        // Then
        assertEquals(childId, childEvent.typedChildId)
        assertEquals(childId.value, childEvent.childId)
        assertEquals(userId, reportEvent.typedUserId)
        assertEquals(childId, reportEvent.typedChildId)
        assertEquals(reportId, reportEvent.typedReportId)
    }
}
