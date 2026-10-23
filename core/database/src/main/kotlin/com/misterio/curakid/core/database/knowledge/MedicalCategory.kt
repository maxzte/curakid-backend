package com.misterio.curakid.core.database.knowledge

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "medical_categories")
@SQLRestriction("deleted_at IS NULL")
class MedicalCategory(

    @Column(name = "name", unique = true, nullable = false)
    var name: String = "",

    @Column(name = "display_name", nullable = false)
    var displayName: String = ""

) : BaseEntity()
