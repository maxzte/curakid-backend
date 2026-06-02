package com.misterio.curakid.core.database.knowledge

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "medical_knowledge")
class MedicalKnowledge(

    @Column(name = "title", nullable = false)
    var title: String = "",

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    var content: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: MedicalCategory? = null,

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(768)")
    var embedding: FloatArray? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: Map<String, Any>? = null

) : BaseEntity()
