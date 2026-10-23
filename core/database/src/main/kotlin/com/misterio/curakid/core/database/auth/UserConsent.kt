package com.misterio.curakid.core.database.auth

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "user_consents")
class UserConsent(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    var user: User? = null,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var consentType: ConsentType = ConsentType.TERMS,

    @Column(nullable = false)
    var accepted: Boolean = false,

    @Column(nullable = false)
    var consentVersion: String = "",

    @Column(nullable = false)
    var acceptedAt: Instant = Instant.now(),

    @Column(nullable = false)
    var ipAddress: String = "",

    @Column(nullable = false)
    var userAgent: String = "",

    @Column(nullable = false)
    var appVersion: String = "",

    var revokedAt: Instant? = null

) : BaseEntity()

enum class ConsentType {
    TERMS,
    PRIVACY_POLICY
}
