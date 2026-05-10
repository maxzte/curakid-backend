package com.misterio.curakid.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface MedicalCategoryRepository : JpaRepository<MedicalCategory, UUID> {
    fun findByName(name: String): MedicalCategory?
}
