package com.misterio.curakid.core.database.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.*

interface UserConsentRepository : JpaRepository<UserConsent, UUID> {

    @Modifying
    @Query("UPDATE UserConsent c SET c.revokedAt = :revokedAt WHERE c.userId = :userId AND c.revokedAt IS NULL")
    fun revokeAllByUserId(
        @Param("userId") userId: UUID,
        @Param("revokedAt") revokedAt: Instant
    )

}
