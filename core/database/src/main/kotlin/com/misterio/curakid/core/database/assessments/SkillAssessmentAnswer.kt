package com.misterio.curakid.core.database.assessments

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.question.Question
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "assessment_answers")
class AssessmentAnswer(

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    var assessment: SkillAssessment? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    var question: Question? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_option_id", nullable = false)
    var selectedOption: AnswerOption? = null,

    @Column(name = "answered_at", nullable = false)
    var answeredAt: Instant = Instant.now()

) : BaseEntity()
