package com.misterio.curakid.core.database

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity : Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private var id: UUID = UUID.randomUUID()

    override fun getId(): UUID = id

    open fun setId(id: UUID) {
        this.id = id
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    open var createdAt: Instant? = null

    @Version
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant? = null

    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = true

    @Column(name = "deleted_at")
    open var deletedAt: Instant? = null

    override fun isNew(): Boolean = updatedAt == null

}
