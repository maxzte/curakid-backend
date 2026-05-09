package com.misterio.curakid.auth

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "token_blacklist")
class TokenBlacklist(
    @Id
    var jti: String = "",

    @Column(name = "expiry_date", nullable = false)
    var expiryDate: Instant = Instant.now()
)
