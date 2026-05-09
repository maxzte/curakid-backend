package com.misterio.curakid.auth

import com.misterio.curakid.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    var user: User? = null,

    @Column(nullable = false, unique = true)
    var token: String = "",

    @Column(name = "expiry_date", nullable = false)
    var expiryDate: Instant = Instant.now()
) : BaseEntity()

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    var user: User? = null,

    @Column(nullable = false, unique = true)
    var token: String = "",

    @Column(name = "expiry_date", nullable = false)
    var expiryDate: Instant = Instant.now()
) : BaseEntity()
