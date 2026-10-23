package com.misterio.curakid.core.database.children

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.auth.User
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "children")
@SQLRestriction("deleted_at IS NULL")
class Child(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = LocalDate.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    var gender: Gender = Gender.MALE,

    @Column(name = "is_premature")
    var isPremature: Boolean = false,

    @Column(name = "gestational_weeks")
    var gestationalWeeks: Int? = null,

    @ElementCollection
    @CollectionTable(name = "child_allergies", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "allergy", columnDefinition = "TEXT")
    @org.hibernate.annotations.BatchSize(size = 50)
    var allergies: List<String> = emptyList(),

    @ElementCollection
    @CollectionTable(name = "child_chronic_conditions", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "condition", columnDefinition = "TEXT")
    @org.hibernate.annotations.BatchSize(size = 50)
    var chronicConditions: List<String> = emptyList(),

    @ElementCollection
    @CollectionTable(name = "child_past_illnesses", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "illness", columnDefinition = "TEXT")
    @org.hibernate.annotations.BatchSize(size = 50)
    var pastIllnesses: List<String> = emptyList(),

    @ElementCollection
    @CollectionTable(name = "child_notes", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "notes", columnDefinition = "TEXT")
    @org.hibernate.annotations.BatchSize(size = 50)
    var notes: List<String> = emptyList(),

) : BaseEntity() {

    fun calculateCorrectedAgeMonths(): Int {
        val chronologicalAge = ChronoUnit.MONTHS.between(birthDate, LocalDate.now()).toInt()
        
        // Corrected age only applies to premature babies (< 40 weeks) up to 2 years
        if (!isPremature || gestationalWeeks == null || gestationalWeeks!! >= 40 || chronologicalAge >= 24) {
            return chronologicalAge
        }

        // Formula: Corrected Age = Chronological Age - (40 - Gestational Weeks) / 4 (weeks to months)
        val weeksEarly = 40 - gestationalWeeks!!
        val monthsEarly = weeksEarly / 4

        return (chronologicalAge - monthsEarly).coerceAtLeast(0)
    }
}


enum class Gender {
    MALE, FEMALE
}
