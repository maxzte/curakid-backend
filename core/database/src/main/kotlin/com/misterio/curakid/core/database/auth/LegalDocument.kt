package com.misterio.curakid.core.database.auth

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "legal_documents")
@SQLRestriction("deleted_at IS NULL")
class LegalDocument(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: ConsentType = ConsentType.TERMS,

    @Column(nullable = false)
    val version: String = "",

    @Column(nullable = false, unique = true)
    val url: String = "",

    isActive: Boolean = false,

) : BaseEntity() {
    init {
        this.isActive = isActive
    }
}
