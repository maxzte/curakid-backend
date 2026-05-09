package com.misterio.curakid.auth

import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    var user: User? = null,

    @Column(nullable = false, unique = true)
    var token: String = "",

    @Column(name = "expiry_date", nullable = false)
    var expiryDate: Instant = Instant.now()
) : BaseEntity()
