package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.notification.DeviceToken
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

class DeviceTokenServiceTest {

    private lateinit var deviceTokenRepository: DeviceTokenRepository
    private lateinit var deviceTokenService: DeviceTokenService

    private val user = User(
        email = "parent@example.com",
        passwordHash = "hashed_pass",
        name = "Parent User"
    ).apply {
        id = UUID.randomUUID()
    }

    @BeforeEach
    fun setUp() {
        deviceTokenRepository = mock()
        deviceTokenService = DeviceTokenService(deviceTokenRepository)
    }

    @Test
    fun `registerToken creates new DeviceToken when token does not exist`() {
        val tokenString = "fcm_test_token_123"
        val request = RegisterDeviceTokenRequest(token = tokenString, deviceType = "ANDROID")

        whenever(deviceTokenRepository.findByToken(tokenString)).thenReturn(null)
        whenever(deviceTokenRepository.save(any<DeviceToken>())).thenAnswer { invocation ->
            val token = invocation.getArgument<DeviceToken>(0)
            token.id = UUID.randomUUID()
            token
        }

        val result = deviceTokenService.registerToken(user, request)

        assertNotNull(result.id)
        assertEquals(tokenString, result.token)
        assertEquals("ANDROID", result.deviceType)

        verify(deviceTokenRepository).save(argThat {
            this.user == user && this.token == tokenString && this.deviceType == "ANDROID" && this.isActive
        })
    }

    @Test
    fun `registerToken updates existing DeviceToken when token exists`() {
        val tokenString = "existing_fcm_token"
        val existingToken = DeviceToken(
            user = null,
            token = tokenString,
            deviceType = "OLD_TYPE",
            lastActiveAt = Instant.now().minusSeconds(3600)
        ).apply {
            id = UUID.randomUUID()
            isActive = false
        }

        val request = RegisterDeviceTokenRequest(token = tokenString, deviceType = "ANDROID")

        whenever(deviceTokenRepository.findByToken(tokenString)).thenReturn(existingToken)
        whenever(deviceTokenRepository.save(existingToken)).thenReturn(existingToken)

        val result = deviceTokenService.registerToken(user, request)

        assertEquals(existingToken.id, result.id)
        assertEquals(tokenString, result.token)
        assertEquals("ANDROID", result.deviceType)
        assertTrue(existingToken.isActive)
        assertEquals(user, existingToken.user)

        verify(deviceTokenRepository).save(existingToken)
    }

    @Test
    fun `removeToken deactivates token when owned by user`() {
        val tokenString = "token_to_remove"
        val existingToken = DeviceToken(
            user = user,
            token = tokenString,
            deviceType = "ANDROID",
            lastActiveAt = Instant.now()
        ).apply {
            id = UUID.randomUUID()
            isActive = true
        }

        whenever(deviceTokenRepository.findByToken(tokenString)).thenReturn(existingToken)

        deviceTokenService.removeToken(user.id, tokenString)

        assertFalse(existingToken.isActive)
        verify(deviceTokenRepository).save(existingToken)
    }

    @Test
    fun `removeToken does nothing when token belongs to another user`() {
        val otherUser = User(email = "other@test.com", passwordHash = "hash", name = "Other").apply {
            id = UUID.randomUUID()
        }
        val tokenString = "other_user_token"
        val existingToken = DeviceToken(
            user = otherUser,
            token = tokenString,
            deviceType = "ANDROID",
            lastActiveAt = Instant.now()
        ).apply {
            id = UUID.randomUUID()
            isActive = true
        }

        whenever(deviceTokenRepository.findByToken(tokenString)).thenReturn(existingToken)

        deviceTokenService.removeToken(user.id, tokenString)

        assertTrue(existingToken.isActive)
        verify(deviceTokenRepository, never()).save(any())
    }
}
