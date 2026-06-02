package com.misterio.curakid.core.database.auth

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "legal_documents")
class LegalDocument(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ConsentType = ConsentType.TERMS,

    @Column(nullable = false)
    val version: String = "",

    @Column(nullable = false, unique = true)
    val url: String = "",

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = false,

) : BaseEntity()
