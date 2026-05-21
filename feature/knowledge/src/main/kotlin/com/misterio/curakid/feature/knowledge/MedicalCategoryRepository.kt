package com.misterio.curakid.feature.knowledge

import com.misterio.curakid.core.database.knowledge.MedicalCategory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface MedicalCategoryRepository : JpaRepository<MedicalCategory, UUID> {
    fun findByName(name: String): MedicalCategory?
}
