package com.misterio.curakid.core.database.knowledge

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.SQLRestriction
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "medical_knowledge")
@SQLRestriction("deleted_at IS NULL")
class MedicalKnowledge(

    @Column(name = "title", nullable = false)
    var title: String = "",

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    var content: String = "",

    @Column(name = "source_id")
    var sourceId: UUID? = null,

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
