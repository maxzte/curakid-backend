package com.misterio.curakid.core.database.assessments

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "skill_assessments")
class SkillAssessment(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    var child: Child? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false)
    var domain: AssessmentDomain = AssessmentDomain.MOTOR_GROSS,

    @Column(name = "score", precision = 5, scale = 2)
    var score: BigDecimal? = null,

    @OneToMany(mappedBy = "assessment", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val answers: MutableList<AssessmentAnswer> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "life_period_id")
    var lifePeriod: LifePeriod? = null,

    @Column(name = "assessment_date", nullable = false)
    var assessmentDate: Instant = Instant.now()

) : BaseEntity()

enum class AssessmentDomain {
    MOTOR_GROSS,        // велика моторика — ходьба, біг, стрибки
    MOTOR_FINE,         // дрібна моторика — захват, малювання
    SPEECH,             // мовлення та мова
    COGNITIVE,          // пізнання, вирішення задач
    SOCIAL_EMOTIONAL,   // соціально-емоційний розвиток
    SELF_CARE,          // самообслуговування — їжа, одяг, туалет
    SENSORY             // сенсорна обробка — якщо є ризик аутизму/СПД
}