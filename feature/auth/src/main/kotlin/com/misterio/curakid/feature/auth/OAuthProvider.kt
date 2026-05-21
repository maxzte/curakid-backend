package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.auth.User
import jakarta.persistence.*

@Entity
@Table(name = "oauth_providers")
class OAuthProvider(

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    var user: User? = null,

    @Column(name = "provider_name", nullable = false)
    var providerName: String = "", // e.g. "google"

    @Column(name = "provider_user_id", nullable = false)
    var providerUserId: String = "" // The subject ID from the provider

) : BaseEntity()
