package com.misterio.curakid.admin

import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "fuzzy_rule_configs")
class FuzzyRuleConfig(
    @Column(name = "config_key", nullable = false, unique = true)
    var configKey: String = "default",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules", columnDefinition = "jsonb")
    var rules: List<Map<String, Any>> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "thresholds", columnDefinition = "jsonb")
    var thresholds: Map<String, Any> = emptyMap()
) : BaseEntity()
