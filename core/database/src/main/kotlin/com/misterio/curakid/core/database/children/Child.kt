package com.misterio.curakid.core.database.children

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.auth.User
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "children")
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

    @ElementCollection
    @CollectionTable(name = "child_allergies", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "allergy")
    var allergies: List<String> = emptyList(),

    @ElementCollection
    @CollectionTable(name = "child_chronic_conditions", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "condition")
    var chronicConditions: List<String> = emptyList(),

    @ElementCollection
    @CollectionTable(name = "child_past_illnesses", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "illness")
    var pastIllnesses: List<String> = emptyList(),

    @ElementCollection
    @CollectionTable(name = "child_notes", joinColumns = [JoinColumn(name = "child_id")])
    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: List<String> = emptyList(),

) : BaseEntity()

enum class Gender {
    MALE, FEMALE
}
