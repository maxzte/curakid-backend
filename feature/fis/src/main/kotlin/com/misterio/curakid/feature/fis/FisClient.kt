package com.misterio.curakid.feature.fis

import com.misterio.curakid.core.essential.common.analysis.IntelligenceProvider
import com.misterio.curakid.core.essential.common.analysis.IntelligenceRequest
import com.misterio.curakid.core.essential.common.analysis.IntelligenceResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration

@Component
class FisClient(
    @Value("\${fis.base-url:http://intelligence:8000}") private val baseUrl: String,
    @Value("\${fis.internal-api-key:change-me}") private val internalApiKey: String,
    @Value("\${fis.timeout-seconds:30}") private val timeoutSeconds: Long
) : IntelligenceProvider {
    private val log = LoggerFactory.getLogger(FisClient::class.java)

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader("X-Internal-Key", internalApiKey)
        .build()

    override fun analyze(request: IntelligenceRequest): IntelligenceResponse {
        val fisRequest = FisRequest(
            childId = request.childId,
            ageMonths = request.ageMonths,
            gender = request.gender,
            wfaZscore = request.wfaZscore,
            hfaZscore = request.hfaZscore,
            bmiZscore = request.bmiZscore,
            hcfaZscore = request.hcfaZscore,
            wfhZscore = request.wfhZscore,
            motorGrossScore = request.motorGrossScore,
            motorFineScore = request.motorFineScore,
            speechScore = request.speechScore,
            cognitiveScore = request.cognitiveScore,
            socialScore = request.socialScore,
            selfCareScore = request.selfCareScore,
            sensoryScore = request.sensoryScore,
            rules = request.rules,
            norms = request.norms,
            wfaVelocity = request.wfaVelocity,
            hfaVelocity = request.hfaVelocity,
            bmiVelocity = request.bmiVelocity,
            hcfaVelocity = request.hcfaVelocity,
            wfhVelocity = request.wfhVelocity,
            regressions = request.regressions
        )

        return try {
            val fisResponse = webClient.post()
                .uri("/analyze")
                .bodyValue(fisRequest)
                .retrieve()
                .bodyToMono(FisResponse::class.java)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume { ex ->
                    log.warn(
                        "FIS service unavailable ({}), using fallback urgency score.",
                        ex.message
                    )
                    Mono.just(fallbackFisResponse())
                }
                .block() ?: fallbackFisResponse()

            IntelligenceResponse(
                urgencyScore = fisResponse.urgencyScore,
                breakdown = fisResponse.breakdown,
                linguisticInterpretations = fisResponse.linguisticInterpretations,
                isFallback = fisResponse.isFallback
            )
        } catch (ex: Exception) {
            log.error("Unexpected error calling FIS service: {}", ex.message)
            val fallback = fallbackFisResponse()
            IntelligenceResponse(
                urgencyScore = fallback.urgencyScore,
                breakdown = fallback.breakdown,
                linguisticInterpretations = fallback.linguisticInterpretations,
                isFallback = fallback.isFallback
            )
        }
    }

    private fun fallbackFisResponse() = FisResponse(
        urgencyScore = BigDecimal("0.5"),
        isFallback = true,
        breakdown = emptyMap(),
        linguisticInterpretations = emptyList()
    )
}
