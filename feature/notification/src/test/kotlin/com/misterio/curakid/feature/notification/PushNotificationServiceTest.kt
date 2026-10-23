package com.misterio.curakid.feature.notification

import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

class PushNotificationServiceTest {

    private lateinit var deviceTokenRepository: DeviceTokenRepository
    private lateinit var pushNotificationService: PushNotificationService

    @BeforeEach
    fun setUp() {
        deviceTokenRepository = mock()
        pushNotificationService = PushNotificationService(deviceTokenRepository, mock())
    }

    @Test
    fun `sendNotificationToUser completes without exception when FirebaseApp is not initialized`() {
        val userId = UUID.randomUUID()
        val dto = PushNotificationDto(
            title = "Вакцинація",
            body = "Заплановано щеплення КПК на завтра"
        )

        assertDoesNotThrow {
            pushNotificationService.sendNotificationToUser(userId, dto)
        }

        // When FirebaseApp is not initialized, it exits early safely
        verifyNoInteractions(deviceTokenRepository)
    }
}
