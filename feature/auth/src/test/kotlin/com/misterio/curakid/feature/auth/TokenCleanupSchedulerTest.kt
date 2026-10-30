package com.misterio.curakid.feature.auth.service

import com.misterio.curakid.core.database.token.RefreshTokenRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class TokenCleanupSchedulerTest {

    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var scheduler: TokenCleanupScheduler

    @BeforeEach
    fun setUp() {
        refreshTokenRepository = mock()
        scheduler = TokenCleanupScheduler(refreshTokenRepository)
    }

    @Test
    fun `cleanupExpiredTokens deletes tokens expired before current instant`() {
        // Arrange
        whenever(refreshTokenRepository.deleteAllByExpiryDateBefore(any())).thenReturn(5)

        // Act
        scheduler.cleanupExpiredTokens()

        // Assert
        verify(refreshTokenRepository).deleteAllByExpiryDateBefore(any<Instant>())
    }

    @Test
    fun `cleanupExpiredTokens handles exceptions gracefully without rethrowing`() {
        // Arrange
        whenever(refreshTokenRepository.deleteAllByExpiryDateBefore(any()))
            .thenThrow(RuntimeException("Database connection failed"))

        // Act & Assert (should not throw)
        scheduler.cleanupExpiredTokens()

        verify(refreshTokenRepository).deleteAllByExpiryDateBefore(any<Instant>())
    }
}
