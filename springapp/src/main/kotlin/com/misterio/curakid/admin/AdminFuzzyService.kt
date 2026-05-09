package com.misterio.curakid.admin

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminFuzzyService(
    private val fuzzyRuleConfigRepository: FuzzyRuleConfigRepository
) {
    fun getRules(configKey: String = "default"): FuzzyRuleConfig {
        return fuzzyRuleConfigRepository.findByConfigKey(configKey)
            .orElseGet { fuzzyRuleConfigRepository.save(FuzzyRuleConfig(configKey = configKey)) }
    }

    @Transactional
    fun updateRules(configKey: String, rules: List<Map<String, Any>>): FuzzyRuleConfig {
        val config = getRules(configKey)
        config.rules = rules
        return fuzzyRuleConfigRepository.save(config)
    }

    @Transactional
    fun updateThresholds(configKey: String, thresholds: Map<String, Any>): FuzzyRuleConfig {
        val config = getRules(configKey)
        config.thresholds = thresholds
        return fuzzyRuleConfigRepository.save(config)
    }
}
