package com.misterio.curakid.admin

import com.misterio.curakid.vaccine.VaccineTemplate
import com.misterio.curakid.vaccine.VaccineTemplateRepository
import com.misterio.curakid.vaccine.VaccineType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdminVaccineService(
    private val vaccineTemplateRepository: VaccineTemplateRepository
) {

    fun getTemplates(): List<AdminVaccineTemplateResponse> =
        vaccineTemplateRepository.findAll().map { it.toResponse() }

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
        return vaccineTemplateRepository.save(template).toResponse()
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
        
        return vaccineTemplateRepository.save(template).toResponse()
    }

    @Transactional
    fun deleteTemplate(id: UUID) {
//        vaccineTemplateRepository.deleteById(id)
    }

    private fun VaccineTemplate.toResponse() = AdminVaccineTemplateResponse(
        id = id!!,
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
