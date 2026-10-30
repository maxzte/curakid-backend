package com.misterio.curakid.core.database.token

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.auth.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "refresh_tokens",
    indexes = [
        Index(name = "idx_refresh_tokens_user_expiry", columnList = "user_id, expiry_date")
    ]
)
class RefreshToken(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    var user: User? = null,

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    var token: String = "",

    @Column(name = "expiry_date", nullable = false)
    var expiryDate: Instant = Instant.now(),

) : BaseEntity()
