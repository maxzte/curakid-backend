package com.misterio.curakid.core.database.exercise

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table


@Entity
@Table(name = "exercise_recommendations")
class ExerciseRecommendation(

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "TEXT", nullable = false)
    var description: String = "",

    @Column(nullable = false)
    var durationDays: Int = 7,

    @Column(nullable = false)
    var childId: Long = 0L,

) : BaseEntity()
