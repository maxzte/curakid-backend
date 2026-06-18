package com.misterio.curakid.security

import com.misterio.curakid.core.database.token.TokenBlacklistRepository
import com.misterio.curakid.core.security.TokenBlacklistService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class TokenBlacklistServiceTest {

    @Mock lateinit var repository: TokenBlacklistRepository
    private lateinit var service: TokenBlacklistService

    @BeforeEach
    fun setUp() {
        service = TokenBlacklistService(repository)
    }

    @Test
    fun `addToBlacklist should save entry with expiry`() {
        val jti = "test-jti"
        val expiry = Instant.now().plusMillis(10000)

        service.addToBlacklist(jti, expiry)

        verify(repository).save(argThat { this.jti == jti })
    }

    @Test
    fun `isTokenBlacklisted should check repository`() {
        whenever(repository.existsByJti("black-jti")).thenReturn(true)
        whenever(repository.existsByJti("clean-jti")).thenReturn(false)

        assertTrue(service.isBlacklisted("black-jti"))
        assertFalse(service.isBlacklisted("clean-jti"))
    }

    @Test
    fun `clearExpiredTokens should call delete with current time`() {
        service.clearExpiredTokens()
        verify(repository).deleteAllByExpiryDateBefore(any())
    }
}
