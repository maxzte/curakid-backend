package com.misterio.curakid.core.database.auth

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "oauth_providers",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_oauth_provider_user", columnNames = ["provider_name", "provider_user_id"])
    ]
)
@SQLRestriction("deleted_at IS NULL")
class OAuthProvider(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    var user: User? = null,

    @Column(name = "provider_name", nullable = false)
    var providerName: String = "", // e.g. "google"

    @Column(name = "provider_user_id", nullable = false)
    var providerUserId: String = "" // The subject ID from the provider

) : BaseEntity()
