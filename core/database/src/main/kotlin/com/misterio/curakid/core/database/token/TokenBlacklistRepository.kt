package com.misterio.curakid.core.database.token

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface TokenBlacklistRepository : JpaRepository<TokenBlacklist, String> {
    fun existsByJti(jti: String): Boolean

    @Modifying
    @Transactional
    @Query("DELETE FROM TokenBlacklist t WHERE t.expiryDate < :now")
    fun deleteAllByExpiryDateBefore(@Param("now") now: Instant): Int
}