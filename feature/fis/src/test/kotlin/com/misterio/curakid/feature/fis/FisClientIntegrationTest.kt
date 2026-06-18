package com.misterio.curakid.feature.fis

import com.misterio.curakid.core.essential.common.analysis.IntelligenceRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FisClientIntegrationTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var fisClient: FisClient

    private val testRequest = IntelligenceRequest(
        childId = "test-child-id",
        ageMonths = 12,
        gender = "MALE",
        wfaZscore = BigDecimal("-1.5"),
        hfaZscore = BigDecimal("-1.0"),
        bmiZscore = BigDecimal("-0.5"),
        hcfaZscore = null,
        wfhZscore = null,
        motorGrossScore = null,
        motorFineScore = null,
        speechScore = null,
        cognitiveScore = null,
        socialScore = null,
        selfCareScore = null,
        sensoryScore = null,
        rules = emptyList()
    )

    @BeforeEach
    fun setUp() {
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
    fun `analyze should return urgency score from FIS service`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"urgencyScore":0.75,"isFallback":false,"breakdown":{"weight":0.8,"motor":0.7},"linguisticInterpretations":["test"]}""")
        )

        val result = fisClient.analyze(testRequest)

        Assertions.assertFalse(result.isFallback)
        Assertions.assertEquals(BigDecimal("0.75"), result.urgencyScore)
        Assertions.assertEquals(2, result.breakdown.size)
        Assertions.assertEquals(listOf("test"), result.linguisticInterpretations)

        val request = mockServer.takeRequest()
        Assertions.assertEquals("/analyze", request.path)
        Assertions.assertEquals("test-secret", request.getHeader("X-Internal-Key"))
        Assertions.assertEquals("POST", request.method)
    }

    @Test
    fun `analyze should return fallback when service returns 500`() {
        mockServer.enqueue(MockResponse().setResponseCode(500))

        val result = fisClient.analyze(testRequest)

        Assertions.assertTrue(result.isFallback)
        Assertions.assertEquals(BigDecimal("0.5"), result.urgencyScore)
    }

    @Test
    fun `analyze should send X-Internal-Key header`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"urgencyScore":0.6,"isFallback":false,"breakdown":{}}""")
        )

        fisClient.analyze(testRequest)

        val recorded = mockServer.takeRequest()
        Assertions.assertEquals("test-secret", recorded.getHeader("X-Internal-Key"))
    }

    @Test
    fun `analyze should return fallback when response is malformed JSON`() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not-json")
        )

        val result = fisClient.analyze(testRequest)

        Assertions.assertTrue(result.isFallback)
        Assertions.assertEquals(BigDecimal("0.5"), result.urgencyScore)
    }

    @Test
    fun `analyze should return fallback when service is unreachable`() {
        val deadPort = mockServer.port
        mockServer.shutdown()

        fisClient = FisClient(
            baseUrl = "http://localhost:$deadPort",
            internalApiKey = "test-secret",
            timeoutSeconds = 1
        )

        val result = fisClient.analyze(testRequest)

        Assertions.assertTrue(result.isFallback)
        Assertions.assertEquals(BigDecimal("0.5"), result.urgencyScore)
    }
}