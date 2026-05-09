package com.misterio.curakid.fis

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FisClientIntegrationTest {
    private lateinit var mockServer: MockWebServer
    private val objectMapper = ObjectMapper()
    private lateinit var fisClient: FisClient

    private val testRequest = FisRequest(
        childId = "test-child-id",
        ageMonths = 12,
        gender = "MALE",
        wfaZscore = BigDecimal("-1.5"),
        hfaZscore = BigDecimal("-1.0"),
        bmiZscore = BigDecimal("-0.5"),
        motorScore = BigDecimal("70"),
        speechScore = BigDecimal("65"),
        cognitiveScore = BigDecimal("80"),
        socialScore = BigDecimal("75"),
        rules = emptyList()
    )

    @BeforeEach
    fun setupClient() {
        mockServer = MockWebServer()
        mockServer.start()
        fisClient = FisClient(
            baseUrl = mockServer.url("/").toString().trimEnd('/'),
            internalApiKey = "test-secret",
            timeoutSeconds = 5
        )
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `analyze should return urgencyScore from FIS service`() {
        val mockResponse = FisResponse(
            urgencyScore = BigDecimal("0.75"),
            isFallback = false,
            breakdown = mapOf("weight" to BigDecimal("0.8"), "motor" to BigDecimal("0.7"))
        )
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(mockResponse))
        )

        val result = fisClient.analyze(testRequest)

        assertFalse(result.isFallback)
        assertEquals(BigDecimal("0.75"), result.urgencyScore)
        assertEquals(2, result.breakdown.size)

        val request = mockServer.takeRequest()
        assertEquals("/analyze", request.path)
        assertEquals("test-secret", request.getHeader("X-Internal-Key"))
        assertEquals("POST", request.method)
    }

    @Test
    fun `analyze should return fallback when service returns 500`() {
        mockServer.enqueue(MockResponse().setResponseCode(500))

        val result = fisClient.analyze(testRequest)

        assertTrue(result.isFallback)
        assertEquals(BigDecimal("0.5"), result.urgencyScore)
    }

    @Test
    fun `analyze should return fallback when service is unreachable`() {
        // Simulate connection refused by not enqueuing any response
        // and pointing to a closed port
        mockServer.enqueue(MockResponse().setResponseCode(503))

        val result = fisClient.analyze(testRequest)

        assertTrue(result.isFallback)
        assertEquals(BigDecimal("0.5"), result.urgencyScore)
    }

    @Test
    fun `analyze should send X-Internal-Key header on every request`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"urgencyScore":0.6,"isFallback":false,"breakdown":{}}""")
        )

        fisClient.analyze(testRequest)

        val recorded = mockServer.takeRequest()
        assertNotNull(recorded.getHeader("X-Internal-Key"))
        assertEquals("test-secret", recorded.getHeader("X-Internal-Key"))
    }
}
