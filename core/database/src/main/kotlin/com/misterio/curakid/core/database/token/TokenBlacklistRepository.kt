package com.misterio.curakid.core.database.token

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface TokenBlacklistRepository : JpaRepository<TokenBlacklist, String> {
    fun existsByJti(jti: String): Boolean
    fun deleteAllByExpiryDateBefore(now: Instant)
}