package com.misterio.curakid.feature.event

import com.misterio.curakid.common.events.ChildCreatedEvent
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.event.*
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class VaccineService(
    private val vaccineEventRepository: VaccineEventRepository,
    private val vaccineTemplateRepository: VaccineTemplateRepository,
    private val childRepository: ChildRepository,
    private val eventRepository: EventRepository
) {

    @EventListener
    fun onChildCreated(event: ChildCreatedEvent) {
        val child = childRepository.findById(event.childId).orElse(null) ?: return
        generateScheduleForChild(child)
        generateDoctorVisitsForChild(child)
    }

    private fun generateScheduleForChild(child: Child) {
        val existing = vaccineEventRepository.findAllByChild(child)
        if (existing.isNotEmpty()) return

        val vaccines = buildVaccineSchedule(child)
        vaccineEventRepository.saveAll(vaccines)
    }

    private fun generateDoctorVisitsForChild(child: Child) {
        val existingDoctorVisits = eventRepository.findAllByChildAndType(child, EventType.DOCTOR)
        if (existingDoctorVisits.isNotEmpty()) return

        val visits = listOf(
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
        eventRepository.saveAll(visits)
    }

    private fun doctorVisit(child: Child, months: Int = 0, days: Int = 0): Event {
        val visitDate = child.birthDate.plusMonths(months.toLong()).plusDays(days.toLong()).atTime(9, 0)
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

    // https://vaccine.org.ua/vakczynacziya-v-ukrayini/vaccinationua-calendar/
    private fun buildVaccineSchedule(child: Child): List<VaccineEvent> {
        val templates = vaccineTemplateRepository.findAll()
        if (templates.isNotEmpty()) {
            return templates.mapNotNull { template ->
                // Apply gender filtering if needed (specifically for HPV/ВПЛ)
                if (template.name == "ВПЛ" && child.gender != Gender.FEMALE) {
                    null
                } else {
                    val date = child.birthDate
                        .plusMonths(template.scheduledAgeMonths?.toLong() ?: 0L)
                        .plusDays(template.scheduledAgeDays?.toLong() ?: 0L)
                        .atTime(9, 0)

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

    private fun determineStatus(date: LocalDateTime): EventStatus {
        return if (date.isBefore(LocalDateTime.now())) {
            EventStatus.DONE
        } else {
            EventStatus.PLANNED
        }
    }

}
