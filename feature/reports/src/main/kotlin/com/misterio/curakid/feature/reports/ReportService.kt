package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.AiReport
import com.misterio.curakid.core.database.reports.AiReportRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.*
import com.misterio.curakid.core.essential.common.events.AiReportGeneratedEvent
import com.misterio.curakid.core.essential.domain.toChildId
import com.misterio.curakid.core.essential.domain.toReportId
import com.misterio.curakid.core.essential.domain.toUserId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Lazy
import org.springframework.core.task.TaskExecutor
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class ReportService(
    private val aiReportRepository: AiReportRepository,
    private val periodSummaryRepository: PeriodSummaryRepository,
    private val childRepository: ChildRepository,
    private val measurementRepository: MeasurementRepository,
    private val transactionManager: PlatformTransactionManager,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: TaskExecutor,
    private val eventPublisher: ApplicationEventPublisher,
    private val reportContextBuilder: ReportContextBuilder,
    private val reportGenerationEngine: ReportGenerationEngine,
    @Lazy private val reportQueueProducer: ReportQueueProducer? = null,
) : ReportProvider {
    private val log = LoggerFactory.getLogger(ReportService::class.java)

    fun getReports(childId: UUID, user: User): List<ReportResponse> {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        return getReports(child)
    }

    fun getReports(child: Child): List<ReportResponse> =
        aiReportRepository.findAllByChildOrderByCreatedAtDesc(child).map { ReportResponse.from(it) }

    fun triggerPeriodReportGeneration(childId: UUID, user: User) {
        val child = childRepository.findByIdAndUser(childId, user)
            .orElseThrow { NoSuchElementException("Child not found") }
        val latestSummary = periodSummaryRepository.findTopByChildOrderByCreatedAtDesc(child)
            .orElseThrow { NoSuchElementException("No period summary found for child") }
        val cid = requireNotNull(child.id) { "Child ID must not be null" }
        val sid = requireNotNull(latestSummary.id) { "Summary ID must not be null" }
        taskExecutor.execute {
            generatePeriodReport(cid, sid)
        }
    }

    fun triggerReportRegeneration(summaryId: UUID, user: User) {
        taskExecutor.execute {
            regenerateReport(user, summaryId)
        }
    }

    fun getReport(reportId: UUID, currentUser: User): ReportResponse {
        val report = aiReportRepository.findByIdAndChildUser(reportId, currentUser)
            .orElseThrow { NoSuchElementException("Report not found") }
        return ReportResponse.from(report)
    }

    fun getReportStatus(reportId: UUID, user: User): ResponseEntity<Boolean> {
        val report = aiReportRepository.findByIdAndChildUser(reportId, user)
            .orElseThrow { NoSuchElementException("Report not found") }
        return ResponseEntity.ok(!report.reportText.isNullOrBlank())
    }

    override fun generateEmergencyReport(childId: UUID, measurementId: UUID): UUID? {
        val context = reportContextBuilder.prepareEmergencyReportContext(childId, measurementId) ?: return null
        val placeholder = createPlaceholderReport(context)
        val task = ReportGenerationTask(
            reportId = placeholder.id,
            childId = childId,
            overrideMeasurementId = measurementId
        )
        val enqueued = reportQueueProducer?.enqueueReportGeneration(task) == true
        if (!enqueued) {
            enqueueReportGeneration(placeholder.id, context)
        }
        return placeholder.id
    }

    override fun generatePeriodReport(childId: UUID, periodId: UUID): UUID? {
        val context = reportContextBuilder.preparePeriodReportContext(childId, periodId) ?: return null
        val placeholder = createPlaceholderReport(context)
        val task = ReportGenerationTask(
            reportId = placeholder.id,
            childId = childId,
            periodSummaryId = periodId
        )
        val enqueued = reportQueueProducer?.enqueueReportGeneration(task) == true
        if (!enqueued) {
            enqueueReportGeneration(placeholder.id, context)
        }
        return placeholder.id
    }

    fun executeReportGeneration(task: ReportGenerationTask) {
        val context: ReportContext = transactionManager.readTx {
            if (task.overrideMeasurementId != null) {
                reportContextBuilder.prepareEmergencyReportContext(task.childId, task.overrideMeasurementId)
            } else if (task.periodSummaryId != null) {
                reportContextBuilder.preparePeriodReportContext(task.childId, task.periodSummaryId)
            } else {
                null
            }
        } ?: throw NoSuchElementException("Report context could not be built for reportId=${task.reportId}")

        runReportGenerationWorkflow(task.reportId, context)
    }

    private fun runReportGenerationWorkflow(reportId: UUID, context: ReportContext) {
        try {
            val generated = reportGenerationEngine.generateReport(context)
            transactionManager.writeTx {
                val report = updatePlaceholderReport(reportId, generated)

                // Publish event for push notification
                if (report != null && !generated.isFallback) {
                    val child = report.child
                    val user = child?.user
                    val uid = user?.id
                    val cid = child?.id
                    val rid = report.id
                    if (child != null && uid != null && cid != null) {
                        eventPublisher.publishEvent(
                            AiReportGeneratedEvent.from(
                                userId = uid.toUserId(),
                                childId = cid.toChildId(),
                                childName = child.name,
                                reportId = rid.toReportId(),
                                isEmergency = context.overrideMeasurementId != null
                            )
                        )
                    }
                }
            }
        } catch (ex: Throwable) {
            log.error(
                "Async report generation failed for reportId={}: {}",
                reportId,
                ex.message,
                ex
            )
            transactionManager.writeTx {
                val fallbackReport = aiReportRepository.findById(reportId)
                    .orElseThrow { NoSuchElementException("Report not found") }
                fallbackReport.apply {
                    reportText = "Звіт тимчасово недоступний."
                    recommendations = "Будь ласка, зверніться до лікаря."
                    isFallback = true
                }
                aiReportRepository.save(fallbackReport)
            }
        }
    }

    private fun saveGeneratedReport(
        context: ReportContext,
        generatedReport: GeneratedReport
    ): AiReport {
        val report = AiReport(
            child = childRepository.getReferenceById(context.childId),
            periodSummary = context.periodSummaryId?.let {
                periodSummaryRepository.getReferenceById(it)
            },
            triggerMeasurement = context.overrideMeasurementId?.let {
                measurementRepository.getReferenceById(it)
            },
            urgencyScore = generatedReport.urgencyScore,
            reportText = generatedReport.reportText,
            recommendations = generatedReport.recommendations,
            isFallback = generatedReport.isFallback,
            faithfulnessScore = generatedReport.faithfulnessScore,
            relevanceScore = generatedReport.relevanceScore
        )
        return aiReportRepository.save(report)
    }

    private fun createPlaceholderReport(context: ReportContext): AiReport {
        val report = AiReport(
            child = childRepository.getReferenceById(context.childId),
            periodSummary = context.periodSummaryId?.let {
                periodSummaryRepository.getReferenceById(it)
            },
            triggerMeasurement = context.overrideMeasurementId?.let {
                measurementRepository.getReferenceById(it)
            },
            urgencyScore = null,
            reportText = null,
            recommendations = null,
            isFallback = false
        )
        return aiReportRepository.save(report)
    }

    private fun enqueueReportGeneration(reportId: UUID, context: ReportContext) {
        CompletableFuture.runAsync({
            runReportGenerationWorkflow(reportId, context)
        }, taskExecutor)
    }

    private fun updatePlaceholderReport(
        reportId: UUID,
        generatedReport: GeneratedReport
    ): AiReport? {
        val report = aiReportRepository.findById(reportId)
            .orElseThrow { NoSuchElementException("Report not found") }
        report.apply {
            urgencyScore = generatedReport.urgencyScore
            reportText = generatedReport.reportText
            recommendations = generatedReport.recommendations
            isFallback = generatedReport.isFallback
            faithfulnessScore = generatedReport.faithfulnessScore
            relevanceScore = generatedReport.relevanceScore
        }
        return aiReportRepository.save(report)
    }

    fun regenerateReport(user: User, summaryId: UUID): ReportResponse {
        val summary = periodSummaryRepository.findById(summaryId)
            .orElseThrow { NoSuchElementException("Period summary not found") }
        if (summary.child?.user?.id != user.id) {
            throw AccessDeniedException("Access denied")
        }
        val childId = requireNotNull(summary.child?.id) { "Child ID must not be null for PeriodSummary" }
        val context = reportContextBuilder.preparePeriodReportContext(childId, summaryId)
            ?: throw IllegalStateException("Unable to prepare report context")
        val generated = reportGenerationEngine.generateReport(context)
        val report = saveGeneratedReport(context, generated)
        return ReportResponse.from(report)
    }
}
