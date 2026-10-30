package com.misterio.curakid.feature.reports

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.io.Serializable
import java.util.UUID

object ReportTopics {
    const val AI_REPORTS = "curakid.ai.reports"
    const val AI_REPORTS_DLQ = "curakid.ai.reports.dlq"
}

data class ReportGenerationTask(
    val reportId: UUID,
    val childId: UUID,
    val periodSummaryId: UUID? = null,
    val overrideMeasurementId: UUID? = null
) : Serializable

@Component
class ReportQueueProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>? = null
) {
    private val log = LoggerFactory.getLogger(ReportQueueProducer::class.java)

    fun enqueueReportGeneration(task: ReportGenerationTask): Boolean {
        return if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(ReportTopics.AI_REPORTS, task.childId.toString(), task)
                log.info("Report task enqueued to Kafka for reportId={}, childId={}", task.reportId, task.childId)
                true
            } catch (e: Exception) {
                log.warn("Failed to publish report task to Kafka: {}. Falling back to async executor.", e.message)
                false
            }
        } else {
            false
        }
    }
}

@Component
class ReportQueueConsumer(
    private val reportService: ReportService
) {
    private val log = LoggerFactory.getLogger(ReportQueueConsumer::class.java)

    @KafkaListener(
        topics = [ReportTopics.AI_REPORTS],
        groupId = "\${spring.kafka.consumer.group-id:curakid-ai-report-group}",
        concurrency = "4"
    )
    fun processReportTask(task: ReportGenerationTask) {
        log.info("Consuming report task from Kafka: reportId={}, childId={}", task.reportId, task.childId)
        try {
            reportService.executeReportGeneration(task)
        } catch (e: Exception) {
            log.error("Error processing report task from Kafka for reportId={}: {}", task.reportId, e.message, e)
            throw e
        }
    }
}
