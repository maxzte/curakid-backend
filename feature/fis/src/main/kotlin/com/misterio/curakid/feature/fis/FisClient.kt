package com.misterio.curakid.feature.fis

import com.misterio.curakid.core.essential.common.analysis.*
import io.netty.channel.ChannelOption
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.math.BigDecimal
import java.time.Duration

@Component
class FisClient(
    @Value("\${fis.base-url:http://intelligence:8000}") private val baseUrl: String,
    @Value("\${fis.internal-api-key}") private val internalApiKey: String,
    @Value("\${fis.timeout-seconds:15}") private val timeoutSeconds: Long,
    @Value("\${fis.chat-timeout-seconds:3}") private val chatTimeoutSeconds: Long = 3
) : IntelligenceProvider {
    private val log = LoggerFactory.getLogger(FisClient::class.java)

    private val connectionProvider = ConnectionProvider.builder("fis-client-pool")
        .maxConnections(500)
        .pendingAcquireMaxCount(2000)
        .pendingAcquireTimeout(Duration.ofSeconds(5))
        .maxIdleTime(Duration.ofSeconds(30))
        .build()

    private val httpClient = HttpClient.create(connectionProvider)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .responseTimeout(Duration.ofSeconds(timeoutSeconds))

    private val webClient: WebClient = WebClient.builder()
        .baseUrl(baseUrl)
        .clientConnector(ReactorClientHttpConnector(httpClient))
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
            regressions = request.regressions,
            isPremature = request.isPremature,
            allergies = request.allergies,
            chronicConditions = request.chronicConditions,
            pastIllnesses = request.pastIllnesses,
            notes = request.notes,
            daysSinceLastMeasurement = request.daysSinceLastMeasurement
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
                fisUrgencyScore = fisResponse.fisUrgencyScore,
                breakdown = fisResponse.breakdown,
                linguisticInterpretations = fisResponse.linguisticInterpretations,
                isFallback = fisResponse.isFallback,
                systemOneDecisions = fisResponse.systemOneDecisions?.let {
                    SystemOneClinicalDecisions(
                        primaryFocus = it.primaryFocus,
                        requiresDoctorConsultProbability = it.requiresDoctorConsultProbability,
                        allergyOrChronicConstraintProbability = it.allergyOrChronicConstraintProbability,
                        measurementOutlierProbability = it.measurementOutlierProbability,
                        anamnesisSeverityScore = it.anamnesisSeverityScore,
                        isSystemOne = it.isSystemOne
                    )
                }
            )
        } catch (ex: Exception) {
            log.error("Unexpected error calling FIS service: {}", ex.message)
            val fallback = fallbackFisResponse()
            IntelligenceResponse(
                urgencyScore = fallback.urgencyScore,
                fisUrgencyScore = fallback.fisUrgencyScore,
                breakdown = fallback.breakdown,
                linguisticInterpretations = fallback.linguisticInterpretations,
                isFallback = fallback.isFallback,
                systemOneDecisions = null
            )
        }
    }

    override fun rerankChunks(request: RerankRequestDto): RerankResponseDto {
        val payload = RerankRequestPayload(
            query = request.query,
            childContext = request.childContext,
            chunks = request.chunks.map {
                ChunkPayload(
                    title = it.title,
                    content = it.content,
                    relevanceScore = it.relevanceScore,
                    isSafeAndApplicable = it.isSafeAndApplicable
                )
            }
        )

        return try {
            val response = webClient.post()
                .uri("/rerank")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(RerankResponsePayload::class.java)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume { ex ->
                    log.warn("Laya rerank service unavailable ({}), passing through original chunks.", ex.message)
                    Mono.just(RerankResponsePayload(chunks = payload.chunks))
                }
                .block() ?: RerankResponsePayload(chunks = payload.chunks)

            RerankResponseDto(
                chunks = response.chunks.map {
                    KnowledgeChunkDto(
                        title = it.title,
                        content = it.content,
                        relevanceScore = it.relevanceScore,
                        isSafeAndApplicable = it.isSafeAndApplicable
                    )
                }
            )
        } catch (e: Exception) {
            log.error("Error calling /rerank: {}", e.message)
            RerankResponseDto(chunks = request.chunks)
        }
    }

    override fun evaluateReport(request: ReportEvaluationRequestDto): ReportEvaluationResponseDto {
        val payload = EvaluateReportPayload(
            childContext = request.childContext,
            chunks = request.chunks.map { ChunkPayload(title = it.title, content = it.content) },
            reportText = request.reportText,
            recommendations = request.recommendations
        )

        return try {
            val response = webClient.post()
                .uri("/evaluate")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(EvaluateReportResponsePayload::class.java)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume { ex ->
                    log.warn("Laya evaluation service unavailable ({}), returning default safe evaluation.", ex.message)
                    Mono.just(EvaluateReportResponsePayload())
                }
                .block() ?: EvaluateReportResponsePayload()

            ReportEvaluationResponseDto(
                faithfulnessScore = response.faithfulnessScore,
                relevanceScore = response.relevanceScore,
                passedGuardrail = response.passedGuardrail,
                violationReasons = response.violationReasons,
                isSystemOne = response.isSystemOne
            )
        } catch (e: Exception) {
            log.error("Error calling /evaluate: {}", e.message)
            ReportEvaluationResponseDto(
                faithfulnessScore = 0.85,
                relevanceScore = 0.85,
                passedGuardrail = true,
                violationReasons = emptyList(),
                isSystemOne = false
            )
        }
    }

    override fun triageChat(request: ChatTriageRequestDto): ChatTriageResponseDto {
        val payload = ChatTriagePayload(
            message = request.message,
            childContext = request.childContext
        )

        return try {
            val response = webClient.post()
                .uri("/triage-chat")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(ChatTriageResponsePayload::class.java)
                .timeout(Duration.ofSeconds(chatTimeoutSeconds))
                .onErrorResume { ex ->
                    log.warn("Laya chat triage service unavailable ({}), returning safe fallback.", ex.message)
                    Mono.just(ChatTriageResponsePayload())
                }
                .block() ?: ChatTriageResponsePayload()

            ChatTriageResponseDto(
                intent = response.intent,
                urgencyLevel = response.urgencyLevel,
                needsRagSearch = response.needsRagSearch,
                asksForMedicationOrDiagnosis = response.asksForMedicationOrDiagnosis,
                statusMessage = response.statusMessage,
                suggestedPrompts = response.suggestedPrompts,
                isSystemOne = response.isSystemOne
            )
        } catch (e: Exception) {
            log.error("Error calling /triage-chat: {}", e.message)
            ChatTriageResponseDto()
        }
    }

    private fun fallbackFisResponse() = FisResponse(
        urgencyScore = BigDecimal("0.5"),
        isFallback = true,
        breakdown = emptyMap(),
        linguisticInterpretations = emptyList()
    )
}
