package com.misterio.curakid.core.database.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeviceTokenRepository : JpaRepository<DeviceToken, UUID> {
    fun findByToken(token: String): DeviceToken?
    fun findAllByUserId(userId: UUID): List<DeviceToken>
    fun findAllByUserIdAndIsActiveTrue(userId: UUID): List<DeviceToken>
    fun findAllByIsActiveTrue(): List<DeviceToken>
    fun deleteAllByUserIdAndToken(userId: UUID, token: String)
    fun deleteAllByUserId(userId: UUID)
}
