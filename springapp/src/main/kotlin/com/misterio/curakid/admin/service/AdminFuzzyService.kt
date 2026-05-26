package com.misterio.curakid.admin.service

import com.misterio.curakid.core.database.knowledge.FuzzyConditionData
import com.misterio.curakid.core.database.knowledge.FuzzyDomain
import com.misterio.curakid.core.database.knowledge.FuzzyRuleEntity
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdminFuzzyService(
    private val fuzzyRuleRepository: FuzzyRuleRepository
) {
    fun getRules(): List<FuzzyRuleEntity> {
        return fuzzyRuleRepository.findAll()
    }

    @Transactional
    fun updateRules(rules: List<Map<String, Any>>): List<FuzzyRuleEntity> {
        // Clear old rules and replace with new ones (simple sync for now)
        fuzzyRuleRepository.deleteAll()
        
        val entities = rules.map { r ->
            val conditionsRaw = r["conditions"] as? List<*> ?: emptyList<Any>()
            FuzzyRuleEntity(
                domain = try {
                    FuzzyDomain.valueOf(r["domain"] as? String ?: "GENERAL")
                } catch (e: Exception) {
                    FuzzyDomain.GENERAL
                },
                conditions = conditionsRaw.filterIsInstance<Map<String, Any>>().map { c -> 
                    FuzzyConditionData(
                        variable = c["variable"] as? String ?: "",
                        term = c["term"] as? String ?: ""
                    ) 
                },
                consequent = r["consequent"] as? String ?: "MEDIUM",
                isActive = (r["isActive"] as? Boolean) ?: (r["active"] as? Boolean) ?: true
            )
        }
        return fuzzyRuleRepository.saveAll(entities)
    }

    @Transactional
    fun toggleRule(id: UUID): FuzzyRuleEntity {
        val rule = fuzzyRuleRepository.findById(id).orElseThrow()
        rule.isActive = !rule.isActive
        return fuzzyRuleRepository.save(rule)
    }
}
