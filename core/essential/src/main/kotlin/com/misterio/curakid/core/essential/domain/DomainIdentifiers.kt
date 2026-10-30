package com.misterio.curakid.core.essential.domain

import java.util.UUID

/**
 * Strongly-typed domain identifiers to prevent Primitive Obsession across module boundaries.
 */
@JvmInline
value class UserId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class ChildId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class ReportId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class PeriodSummaryId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class MeasurementId(val value: UUID) {
    override fun toString(): String = value.toString()
}

@JvmInline
value class QuestionId(val value: UUID) {
    override fun toString(): String = value.toString()
}

fun UUID.toUserId(): UserId = UserId(this)
fun UUID.toChildId(): ChildId = ChildId(this)
fun UUID.toReportId(): ReportId = ReportId(this)
fun UUID.toPeriodSummaryId(): PeriodSummaryId = PeriodSummaryId(this)
fun UUID.toMeasurementId(): MeasurementId = MeasurementId(this)
fun UUID.toQuestionId(): QuestionId = QuestionId(this)
