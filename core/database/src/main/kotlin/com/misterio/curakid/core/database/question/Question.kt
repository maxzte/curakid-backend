package com.misterio.curakid.core.database.question

import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.development.LifePeriod
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal

@Entity
@Table(
    name = "questions",
    indexes = [
        Index(name = "idx_questions_period_domain", columnList = "life_period_id, domain")
    ]
)
@SQLRestriction("deleted_at IS NULL")
class Question(

    @Column(nullable = false, columnDefinition = "TEXT")
    var text: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var domain: AssessmentDomain = AssessmentDomain.MOTOR_GROSS,

    @Column(name = "weight", precision = 3, scale = 2)
    @Min(0L)
    @Max(1L)
    var weight: BigDecimal = BigDecimal("1.0"),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "life_period_id")
    var lifePeriod: LifePeriod? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_id")
    var prerequisite: Question? = null,

    @OneToMany(mappedBy = "question", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    @BatchSize(size = 50)
    val options: MutableList<AnswerOption> = mutableListOf()

) : BaseEntity()
