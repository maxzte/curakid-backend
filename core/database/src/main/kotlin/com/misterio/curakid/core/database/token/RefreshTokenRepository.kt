package com.misterio.curakid.core.database.token

import com.misterio.curakid.core.database.auth.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findAllByUser(user: User): List<RefreshToken>

    fun findByToken(token: String): Optional<RefreshToken>

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    fun deleteByUser(@Param("user") user: User): Int

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :now")
    fun deleteAllByExpiryDateBefore(@Param("now") now: java.time.Instant): Int

}