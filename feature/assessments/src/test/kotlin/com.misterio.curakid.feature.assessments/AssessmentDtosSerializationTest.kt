package com.misterio.curakid.feature.assessments

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AssessmentDtosSerializationTest {

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `PeriodReadinessResponse serializes isReady with the expected JSON name`() {
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
            )
        )

        val json = mapper.writeValueAsString(response)

        assertTrue(json.contains("\"isReady\":true"), json)
        assertFalse(json.contains("\"ready\":"), json)
    }
}
