package com.misterio.curakid.core.database.token

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, UUID> {
    fun findByToken(token: String): Optional<EmailVerificationToken>
}
