package com.misterio.curakid.feature.event

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventRepository
import com.misterio.curakid.core.database.event.EventStatus
import com.misterio.curakid.core.database.event.EventType
import com.misterio.curakid.core.database.event.VaccineEvent
import com.misterio.curakid.core.database.event.VaccineEventRepository
import com.misterio.curakid.core.database.event.VaccineTemplate
import com.misterio.curakid.core.database.event.VaccineTemplateRepository
import com.misterio.curakid.core.essential.common.events.ChildCreatedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import java.time.Instant
import java.time.ZoneOffset

@Service
class VaccineService(
    private val vaccineEventRepository: VaccineEventRepository,
    private val vaccineTemplateRepository: VaccineTemplateRepository,
    private val childRepository: ChildRepository,
    private val eventRepository: EventRepository,
    private val transactionManager: PlatformTransactionManager
) {

    @EventListener
    fun onChildCreated(event: ChildCreatedEvent) {
        // Phase 1: Read Context
        val context = transactionManager.readTx {
            val child = childRepository.findById(event.typedChildId.value).orElse(null) ?: return@readTx null
            val existingVaccines = vaccineEventRepository.findAllByChild(child)
            val existingDoctorVisits = eventRepository.findAllByChildAndType(child, EventType.DOCTOR)
            val templates = vaccineTemplateRepository.findAllByIsActiveTrue()
            ScheduleContext(child, existingVaccines.isNotEmpty(), existingDoctorVisits.isNotEmpty(), templates)
        } ?: return

        // Phase 2: Domain Processing
        val vaccinesToSave = if (!context.hasExistingVaccines) buildVaccineSchedule(context.child, context.templates) else emptyList()
        val visitsToSave = if (!context.hasExistingDoctorVisits) buildDoctorVisits(context.child) else emptyList()

        if (vaccinesToSave.isEmpty() && visitsToSave.isEmpty()) return

        // Phase 3: Write Transaction
        transactionManager.writeTx {
            if (vaccinesToSave.isNotEmpty()) {
                vaccineEventRepository.saveAll(vaccinesToSave)
            }
            if (visitsToSave.isNotEmpty()) {
                eventRepository.saveAll(visitsToSave)
            }
        }
    }

    private data class ScheduleContext(
        val child: Child,
        val hasExistingVaccines: Boolean,
        val hasExistingDoctorVisits: Boolean,
        val templates: List<VaccineTemplate>
    )

    private fun buildDoctorVisits(child: Child): List<Event> {
        return listOf(
            doctorVisit(child, months = 1),
            doctorVisit(child, months = 1, days = 15),
            doctorVisit(child, months = 2),
            doctorVisit(child, months = 3),
            doctorVisit(child, months = 4),
            doctorVisit(child, months = 5),
            doctorVisit(child, months = 6),
            doctorVisit(child, months = 9),
            doctorVisit(child, months = 11),
            doctorVisit(child, months = 12),
            doctorVisit(child, months = 18),
            doctorVisit(child, months = 24),
            doctorVisit(child, months = 36),
            doctorVisit(child, months = 48),
            doctorVisit(child, months = 60),
            doctorVisit(child, months = 72)
        )
    }

    private fun doctorVisit(child: Child, months: Int = 0, days: Int = 0): Event {
        val visitDate = child.birthDate.plusMonths(months.toLong()).plusDays(days.toLong()).atTime(9, 0)
            .toInstant(ZoneOffset.UTC)
        return Event(
            title = "Обовʼязковий огляд лікаря",
            type = EventType.DOCTOR,
            date = visitDate,
            description = "Плановий профілактичний огляд дитини у віці ${formatAge(months, days)}",
            child = child,
            status = determineStatus(visitDate)
        )
    }

    private fun formatAge(months: Int, days: Int): String {
        return when {
            months == 0 && days > 0 -> "$days дн."
            months > 0 && days == 0 -> {
                if (months >= 12 && months % 12 == 0) {
                    when (val years = months / 12) {
                        1 -> "$years рік"
                        in 2..4 -> "$years роки"
                        else -> "$years років"
                    }
                } else {
                    "$months міс."
                }
            }

            months > 0 && days > 0 -> "$months міс. $days дн."
            else -> ""
        }
    }

    private fun buildVaccineSchedule(child: Child, templates: List<VaccineTemplate>): List<VaccineEvent> {
        if (templates.isNotEmpty()) {
            return templates.mapNotNull { template ->
                if (template.name == "ВПЛ" && child.gender != Gender.FEMALE) {
                    null
                } else {
                    val date = child.birthDate
                        .plusMonths(template.scheduledAgeMonths?.toLong() ?: 0L)
                        .plusDays(template.scheduledAgeDays?.toLong() ?: 0L)
                        .atTime(9, 0)
                        .toInstant(ZoneOffset.UTC)

                    VaccineEvent(
                        template = template,
                        status = determineStatus(date),
                        child = child,
                        date = date,
                        title = template.name
                    )
                }
            }
        }
        return emptyList()
    }

    private fun determineStatus(date: Instant): EventStatus {
        return if (date.isBefore(Instant.now())) {
            EventStatus.DONE
        } else {
            EventStatus.PLANNED
        }
    }
}
