package com.misterio.curakid.admin.service

import com.misterio.curakid.admin.AdminVaccineTemplateResponse
import com.misterio.curakid.admin.CreateVaccineTemplateRequest
import com.misterio.curakid.admin.vaccines.*
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.event.*
import com.misterio.curakid.core.essential.common.events.CatalogUpdatedEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import com.misterio.curakid.core.database.writeTx
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

@Service
class AdminVaccineService(
    private val vaccineTemplateRepository: VaccineTemplateRepository,
    private val vaccineCalendarClient: VaccineCalendarClient,
    private val vaccineCalendarParser: VaccineCalendarParser,
    private val vaccineCalendarDiffService: VaccineCalendarDiffService,
    private val childRepository: ChildRepository,
    private val vaccineEventRepository: VaccineEventRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionManager: PlatformTransactionManager? = null
) {

    private fun publishCatalogSync() {
        eventPublisher.publishEvent(CatalogUpdatedEvent("VACCINES"))
    }

    fun getTemplates(): List<AdminVaccineTemplateResponse> =
        vaccineTemplateRepository.findAllByIsActiveTrue().map { it.toResponse() }

    @Transactional
    fun createTemplate(request: CreateVaccineTemplateRequest): AdminVaccineTemplateResponse {
        val template = VaccineTemplate(
            name = request.name,
            disease = request.disease,
            doseNumber = request.doseNumber,
            totalDoses = request.totalDoses,
            type = VaccineType.valueOf(request.type),
            scheduledAgeMonths = request.scheduledAgeMonths,
            scheduledAgeDays = request.scheduledAgeDays,
            notes = request.notes
        )
        val saved = vaccineTemplateRepository.save(template).toResponse()
        publishCatalogSync()
        return saved
    }

    @Transactional
    fun updateTemplate(id: UUID, request: CreateVaccineTemplateRequest): AdminVaccineTemplateResponse {
        val template = vaccineTemplateRepository.findById(id)
            .orElseThrow { NoSuchElementException("Template not found") }

        template.apply {
            name = request.name
            disease = request.disease
            doseNumber = request.doseNumber
            totalDoses = request.totalDoses
            type = VaccineType.valueOf(request.type)
            scheduledAgeMonths = request.scheduledAgeMonths
            scheduledAgeDays = request.scheduledAgeDays
            notes = request.notes
        }

        val saved = vaccineTemplateRepository.save(template).toResponse()
        publishCatalogSync()
        return saved
    }

    @Transactional
    fun deleteTemplate(id: UUID) {
        val template = vaccineTemplateRepository.findById(id)
            .orElseThrow { NoSuchElementException("Template not found") }
        template.isActive = false
        vaccineTemplateRepository.save(template)
        publishCatalogSync()
    }

    fun previewSync(): VaccineSyncPreviewResponse {
        val html = vaccineCalendarClient.fetchCalendarHtml()
        val parsed = vaccineCalendarParser.parse(html)
        return vaccineCalendarDiffService.calculateDiff(parsed)
    }

    fun applySync(request: ApplyVaccineSyncRequest): VaccineSyncPreviewResponse {
        val action = {
            val activeTemplates = vaccineTemplateRepository.findAllByIsActiveTrue()
            val map = activeTemplates.associateBy {
                Triple(it.name.trim().lowercase(), it.type.name, it.doseNumber ?: 1)
            }

            for (item in request.toAdd) {
                val key = Triple(item.name.trim().lowercase(), item.type, item.doseNumber)
                if (!map.containsKey(key)) {
                    val template = VaccineTemplate(
                        name = item.name,
                        disease = item.disease,
                        doseNumber = item.doseNumber,
                        totalDoses = item.totalDoses,
                        type = VaccineType.valueOf(item.type),
                        scheduledAgeMonths = item.scheduledAgeMonths,
                        scheduledAgeDays = item.scheduledAgeDays,
                        notes = item.notes
                    )
                    vaccineTemplateRepository.save(template)
                }
            }

            for (item in request.toUpdate) {
                val key = Triple(item.name.trim().lowercase(), item.type, item.doseNumber)
                val existing = map[key]
                if (existing != null) {
                    existing.disease = item.disease
                    existing.totalDoses = item.totalDoses
                    existing.scheduledAgeMonths = item.scheduledAgeMonths
                    existing.scheduledAgeDays = item.scheduledAgeDays
                    if (!item.notes.isNullOrBlank()) {
                        existing.notes = item.notes
                    }
                    vaccineTemplateRepository.save(existing)
                }
            }

            publishCatalogSync()
        }

        if (transactionManager != null) {
            transactionManager.writeTx { action() }
        } else {
            action()
        }

        // previewSync performs an outbound HTTP fetch; executed outside DB transaction
        return previewSync()
    }

    fun backfillForExistingChildren(): BackfillSummaryResponse {
        val templates = vaccineTemplateRepository.findAllByIsActiveTrue()
        if (templates.isEmpty()) {
            return BackfillSummaryResponse(0, 0, 0, 0)
        }

        var totalChildrenCount = 0
        var createdCount = 0
        var skippedCount = 0
        var plannedCount = 0
        val now = Instant.now()

        var page = 0
        val pageSize = 500

        while (true) {
            val childrenPage = childRepository.findAllByIsActiveTrue(PageRequest.of(page, pageSize))
            if (childrenPage.isEmpty) break

            totalChildrenCount += childrenPage.numberOfElements

            val batchAction = {
                for (child in childrenPage.content) {
                    val existingEvents = vaccineEventRepository.findAllByChild(child)
                    val existingTemplateIds = existingEvents.mapNotNull { it.template?.id }.toSet()
                    val toCreate = mutableListOf<VaccineEvent>()

                    for (template in templates) {
                        if (existingTemplateIds.contains(template.id)) continue
                        if (template.name == "ВПЛ" && child.gender != Gender.FEMALE) continue

                        val date = child.birthDate
                            .plusMonths(template.scheduledAgeMonths?.toLong() ?: 0L)
                            .plusDays(template.scheduledAgeDays?.toLong() ?: 0L)
                            .atTime(9, 0)
                            .toInstant(ZoneOffset.UTC)

                        val status = if (date.isBefore(now)) {
                            skippedCount++
                            EventStatus.SKIPPED
                        } else {
                            plannedCount++
                            EventStatus.PLANNED
                        }

                        toCreate.add(
                            VaccineEvent(
                                template = template,
                                status = status,
                                child = child,
                                date = date,
                                title = template.name
                            )
                        )
                    }

                    if (toCreate.isNotEmpty()) {
                        vaccineEventRepository.saveAll(toCreate)
                        createdCount += toCreate.size
                    }
                }
            }

            if (transactionManager != null) {
                transactionManager.writeTx { batchAction() }
            } else {
                batchAction()
            }

            if (!childrenPage.hasNext()) break
            page++
        }

        return BackfillSummaryResponse(
            totalChildren = totalChildrenCount,
            createdEventsCount = createdCount,
            skippedEventsCount = skippedCount,
            plannedEventsCount = plannedCount
        )
    }

    private fun VaccineTemplate.toResponse() = AdminVaccineTemplateResponse(
        id = requireNotNull(id) { "VaccineTemplate ID must not be null" },
        name = name,
        disease = disease,
        doseNumber = doseNumber,
        totalDoses = totalDoses,
        type = type.name,
        scheduledAgeMonths = scheduledAgeMonths,
        scheduledAgeDays = scheduledAgeDays,
        notes = notes
    )
}
