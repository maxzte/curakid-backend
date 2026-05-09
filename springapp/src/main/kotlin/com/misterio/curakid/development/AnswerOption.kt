package com.misterio.curakid.development

import com.misterio.curakid.common.BaseEntity
import com.fasterxml.jackson.annotation.JsonIgnore
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
    var value: String = "",

    @Column(name = "display_order", nullable = false)
    var order: Int = 0

) : BaseEntity()

