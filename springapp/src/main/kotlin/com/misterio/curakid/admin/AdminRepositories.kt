package com.misterio.curakid.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface FuzzyRuleConfigRepository : JpaRepository<FuzzyRuleConfig, UUID> {
    fun findByConfigKey(configKey: String): Optional<FuzzyRuleConfig>
}
