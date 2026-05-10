package com.misterio.curakid.knowledge

import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "medical_categories")
class MedicalCategory(
    @Column(name = "name", unique = true, nullable = false)
    var name: String = "",

    @Column(name = "display_name", nullable = false)
    var displayName: String = ""
) : BaseEntity()
