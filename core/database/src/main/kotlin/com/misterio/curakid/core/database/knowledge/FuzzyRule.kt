package com.misterio.curakid.core.database.knowledge


import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "fuzzy_rules")
class FuzzyRuleEntity(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var domain: FuzzyDomain = FuzzyDomain.GENERAL,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var conditions: List<FuzzyConditionData> = emptyList(),

    @Column(nullable = false)
    var consequent: String = "",
    
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

) : BaseEntity()

data class FuzzyConditionData(
    var variable: String = "",
    var term: String = ""
)

enum class FuzzyDomain {
    GENERAL,
    MOTOR,
    SPEECH,
    COGNITIVE,
    SOCIAL,
    PHYSICAL
}
