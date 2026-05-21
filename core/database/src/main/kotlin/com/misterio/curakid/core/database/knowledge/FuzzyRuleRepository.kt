package com.misterio.curakid.core.database.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FuzzyRuleRepository : JpaRepository<FuzzyRuleEntity, UUID> {
    fun findAllByIsActiveTrue(): List<FuzzyRuleEntity>
}
