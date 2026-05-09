package com.misterio.curakid.assessments

import com.misterio.curakid.children.Child
import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "skill_assessments")
class SkillAssessment(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false)
    var domain: AssessmentDomain = AssessmentDomain.MOTOR,

    @Column(name = "score", precision = 5, scale = 2)
    var score: BigDecimal? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    var rawData: Map<String, Any>? = null,

    @Column(name = "assessment_date", nullable = false)
    var assessmentDate: LocalDateTime = LocalDateTime.now()

) : BaseEntity()

enum class AssessmentDomain {
    MOTOR, SPEECH, COGNITIVE, SOCIAL
}
