package com.misterio.curakid.core.database.token

import com.misterio.curakid.core.database.auth.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findByUser(user: User): Optional<RefreshToken>

    fun findByToken(token: String): Optional<RefreshToken>

    @Modifying
    @Transactional
    fun deleteByUser(user: User): Int

}