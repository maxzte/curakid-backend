package com.misterio.curakid

import com.misterio.curakid.feature.assessments.DomainReadinessResponse
import com.misterio.curakid.feature.assessments.PeriodReadinessResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.redis.serializer.RedisSerializer

class ReadinessCacheSerializationTest {

    @Test
    fun `Redis JSON serializer round-trips PeriodReadinessResponse`() {
        val serializer = RedisSerializer.json()
        val response = PeriodReadinessResponse(
            isReady = true,
            completionPercent = 100,
            requiredPercent = 80,
            domainStatus = listOf(
                DomainReadinessResponse(
                    domain = "MOTOR_GROSS",
                    completionPercent = 100,
                    isReady = true,
                    requiredPercent = 50
                )
            ),
            reasons = emptyList(),
            canTransitionEarly = false
        )

        val bytes = serializer.serialize(response)
        val restored = serializer.deserialize(bytes) as PeriodReadinessResponse

        assertEquals(response, restored)
    }
}
