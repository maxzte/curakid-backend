package com.misterio.curakid.knowledge

import com.misterio.curakid.common.BaseEntity
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

    @Column(name = "category")
    var category: String? = null,

    // 1024-dim vector from intfloat/multilingual-e5-large, stored as float array
    @Column(name = "embedding", columnDefinition = "vector(1024)")
    var embedding: FloatArray? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: Map<String, Any>? = null
) : BaseEntity()
