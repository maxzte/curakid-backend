package com.misterio.curakid.core.database.knowledge


import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "fuzzy_rules")
@SQLRestriction("deleted_at IS NULL")
class FuzzyRuleEntity(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var domain: FuzzyDomain = FuzzyDomain.GENERAL,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var conditions: List<FuzzyConditionData> = emptyList(),

    @Column(nullable = false)
    var consequent: String = "",
    
    isActive: Boolean = true

) : BaseEntity() {
    init {
        this.isActive = isActive
    }
}

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
