package com.misterio.curakid.auth

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.Date

class TokenBlacklistServiceTest {

    private lateinit var repository: TokenBlacklistRepository
    private lateinit var service: TokenBlacklistService

    @BeforeEach
    fun setUp() {
        repository = mock()
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
