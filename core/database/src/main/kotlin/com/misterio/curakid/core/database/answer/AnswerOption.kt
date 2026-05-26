package com.misterio.curakid.core.database.answer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.question.Question
import jakarta.persistence.*

@Entity
@Table(name = "answer_option")
class AnswerOption(

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    var question: Question? = null,

    @Column(nullable = false)
    var text: String = "",

    @Column(nullable = false)
    var value: Double = 0.0,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0

) : BaseEntity()

