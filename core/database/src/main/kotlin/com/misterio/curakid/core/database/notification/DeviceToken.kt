package com.misterio.curakid.core.database.notification

import com.misterio.curakid.core.database.BaseEntity
import com.misterio.curakid.core.database.auth.User
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "user_device_tokens")
class DeviceToken(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(name = "token", unique = true, nullable = false, length = 512)
    var token: String = "",

    @Column(name = "device_type", nullable = false, length = 32)
    var deviceType: String = "ANDROID",

    @Column(name = "last_active_at", nullable = false)
    var lastActiveAt: Instant = Instant.now()

) : BaseEntity()
