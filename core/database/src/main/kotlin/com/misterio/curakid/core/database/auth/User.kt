package com.misterio.curakid.core.database.auth

import com.misterio.curakid.core.database.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
class User(

    @Column(unique = true, nullable = false)
    var email: String = "",

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",

    @Column(name = "name", nullable = false)
    var name: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role = Role.ROLE_USER,

    isActive: Boolean = false,

    @Column(name = "onboarding_completed", nullable = false)
    var onboardingCompleted: Boolean = false,

    @Column(name = "verification_code")
    var verificationCode: String? = null,

    @Column(name = "verification_code_expiry")
    var verificationCodeExpiry: Instant? = null,

    @Column(name = "reset_code")
    var resetCode: String? = null,

    @Column(name = "reset_code_expiry")
    var resetCodeExpiry: Instant? = null,

    @Column(name = "failed_verification_attempts", nullable = false)
    var failedVerificationAttempts: Int = 0,

    @Column(name = "failed_reset_attempts", nullable = false)
    var failedResetAttempts: Int = 0

) : BaseEntity(), UserDetails {

    init {
        this.isActive = isActive
    }

    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority(role.name))
    override fun getPassword(): String = passwordHash
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = isActive
}

enum class Role {
    ROLE_USER,
    ROLE_ADMIN
}
