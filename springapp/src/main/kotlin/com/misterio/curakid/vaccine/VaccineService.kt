package com.misterio.curakid.vaccine

import com.misterio.curakid.auth.User
import com.misterio.curakid.children.Child
import com.misterio.curakid.children.ChildRepository
import com.misterio.curakid.children.Gender
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import java.util.*

@Service
class VaccineService(
    private val vaccineRepository: VaccineRepository,
    private val vaccineTemplateRepository: VaccineTemplateRepository
) {

    fun getVaccines(childId: UUID): List<VaccineResponse> =
        vaccineRepository.findAllByChildIdOrderByScheduledAgeMonthsAsc(childId)
            .map { it.toResponse() }

    fun updateVaccineStatus(
        vaccineId: UUID,
        request: UpdateVaccineRequest,
        user: User
    ): VaccineResponse {
        val vaccine = vaccineRepository.findById(vaccineId)
            .orElseThrow { NoSuchElementException("Vaccine not found") }

        if (vaccine.child?.user?.id != user.id) {
            throw AccessDeniedException("Access denied")
        }

        vaccine.apply {
            status = request.status
            administeredDate = request.administeredDate
            batchNumber = request.batchNumber
            administeredBy = request.administeredBy
            notes = request.notes
        }

        return vaccineRepository.save(vaccine).toResponse()
    }

    fun generateScheduleForChild(child: Child): List<Vaccine> {
        val existing = vaccineRepository.findAllByChild(child)
        if (existing.isNotEmpty()) return existing

        val vaccines = buildVaccineSchedule(child)
        return vaccineRepository.saveAll(vaccines)
    }

    private fun buildVaccineSchedule(child: Child): List<Vaccine> {
        val templates = vaccineTemplateRepository.findAll()
        if (templates.isNotEmpty()) {
            return templates.mapNotNull { template ->
                // Apply gender filtering if needed (specifically for HPV/ВПЛ)
                if (template.name == "ВПЛ" && child.gender != Gender.FEMALE) {
                    null
                } else {
                    Vaccine(
                        child = child,
                        name = template.name,
                        disease = template.disease,
                        scheduledAgeMonths = template.scheduledAgeMonths,
                        scheduledAgeDays = template.scheduledAgeDays,
                        doseNumber = template.doseNumber,
                        totalDoses = template.totalDoses,
                        type = template.type,
                        notes = template.notes,
                        status = VaccineStatus.PLANNED
                    )
                }
            }
        }

        // Fallback to hardcoded schedule if DB is empty
        // https://vaccine.org.ua/vakczynacziya-v-ukrayini/vaccinationua-calendar/
        return listOf(
            vaccine(child, "БЦЖ", "Туберкульоз", days = 1, dose = 1, total = 1),
            vaccine(child, "Гепатит В", "Гепатит В", months = 2, dose = 1, total = 4),
            vaccine(child, "Гепатит В", "Гепатит В", months = 4, dose = 2, total = 4),
            vaccine(child, "Гепатит В", "Гепатит В", months = 6, dose = 3, total = 4),
            vaccine(child, "Гепатит В", "Гепатит В", months = 18, dose = 4, total = 4),
            vaccine(child, "АКДП", "Дифтерія/Правець/Кашлюк", months = 2, dose = 1, total = 3),
            vaccine(child, "АКДП", "Дифтерія/Правець/Кашлюк", months = 4, dose = 2, total = 3),
            vaccine(child, "АКДП", "Дифтерія/Правець/Кашлюк", months = 6, dose = 3, total = 3),
            vaccine(
                child,
                "АКДП",
                "Дифтерія/Правець/Кашлюк",
                months = 18,
                dose = 1,
                total = 3,
                type = VaccineType.REVACCINATION
            ),
            vaccine(child, "АДП", "Дифтерія/Правець", months = 72, dose = 2, total = 3, type = VaccineType.REVACCINATION),
            vaccine(
                child,
                "АДП-М",
                "Дифтерія/Правець",
                months = 192,
                dose = 3,
                total = 3,
                type = VaccineType.REVACCINATION
            ),
            vaccine(child, "ІПВ", "Поліомієліт", months = 2, dose = 1, total = 5),
            vaccine(child, "ІПВ", "Поліомієліт", months = 4, dose = 2, total = 5),
            vaccine(child, "ІПВ", "Поліомієліт", months = 6, dose = 3, total = 5),
            vaccine(child, "ІПВ", "Поліомієліт", months = 18, dose = 4, total = 5, type = VaccineType.REVACCINATION),
            vaccine(child, "ІПВ", "Поліомієліт", months = 72, dose = 5, total = 5, type = VaccineType.REVACCINATION),
            vaccine(child, "Hib", "Hib-інфекція", months = 2, dose = 1, total = 4),
            vaccine(child, "Hib", "Hib-інфекція", months = 4, dose = 2, total = 4),
            vaccine(child, "Hib", "Hib-інфекція", months = 6, dose = 3, total = 4),
            vaccine(child, "Hib", "Hib-інфекція", months = 18, dose = 4, total = 4, type = VaccineType.REVACCINATION),
            vaccine(child, "КПК", "Кір/Паротит/Краснуха", months = 12, dose = 1, total = 2),
            vaccine(child, "КПК", "Кір/Паротит/Краснуха", months = 48, dose = 2, total = 2),
            // ВПЛ тільки для дівчат — перевіряємо гендер
            *if (child.gender == Gender.FEMALE) arrayOf(
                vaccine(child, "ВПЛ", "ВПЛ-інфекція", months = 144, dose = 1, total = 1)
            ) else emptyArray()
        )
    }

    private fun vaccine(
        child: Child,
        name: String,
        disease: String,
        months: Int? = null,
        days: Int? = null,
        dose: Int,
        total: Int,
        type: VaccineType = VaccineType.VACCINATION,
    ) = Vaccine(
        child = child,
        name = name,
        disease = disease,
        scheduledAgeMonths = months,
        scheduledAgeDays = days,
        doseNumber = dose,
        totalDoses = total,
        type = type,
        status = VaccineStatus.PLANNED
    )

    private fun Vaccine.toResponse() = VaccineResponse(
        id = id!!,
        name = name,
        disease = disease,
        doseNumber = doseNumber,
        totalDoses = totalDoses,
        type = type,
        scheduledAgeMonths = scheduledAgeMonths,
        scheduledAgeDays = scheduledAgeDays,
        administeredDate = administeredDate,
        batchNumber = batchNumber,
        administeredBy = administeredBy,
        notes = notes,
        status = status
    )
}
