package com.misterio.curakid.fis

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.ExchangeStrategies
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import com.fasterxml.jackson.databind.ObjectMapper

@Component
class FisClient(
    @Value("\${fis.base-url:http://intelligence:8000}") private val baseUrl: String,
    @Value("\${fis.internal-api-key:change-me}") private val internalApiKey: String,
    @Value("\${fis.timeout-seconds:5}") private val timeoutSeconds: Long
) {
    private val log = LoggerFactory.getLogger(FisClient::class.java)
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val exchangeStrategies = ExchangeStrategies.builder()
        .codecs { configurer ->
            configurer.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper))
            configurer.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper))
        }
        .build()

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .exchangeStrategies(exchangeStrategies)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader("X-Internal-Key", internalApiKey)
        .build()

    fun analyze(request: FisRequest): FisResponse {
        return try {
            webClient.post()
                .uri("/analyze")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(FisResponse::class.java)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume { ex ->
                    log.warn("FIS service unavailable ({}), using fallback urgency score.", ex.message)
                    Mono.just(fallbackResponse())
                }
                .block()
                ?: fallbackResponse()
        } catch (ex: Exception) {
            log.error("Unexpected error calling FIS service: {}", ex.message)
            fallbackResponse()
        }
    }

    private fun fallbackResponse() = FisResponse(
        urgencyScore = BigDecimal("0.5"),
        isFallback = true,
        breakdown = emptyMap()
    )
}
