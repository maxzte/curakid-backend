package com.misterio.curakid.knowledge

import com.misterio.curakid.fis.FisClient
import com.misterio.curakid.fis.FisRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.Duration

data class EmbeddingRequest(val text: String)
data class EmbeddingResponse(val embedding: List<Float>)

data class KnowledgeChunk(
    val id: String,
    val title: String,
    val content: String,
    val category: String?
)

@Service
class KnowledgeService(
    private val medicalKnowledgeRepository: MedicalKnowledgeRepository,
    @Value("\${fis.base-url:http://intelligence:8000}") private val fisBaseUrl: String,
    @Value("\${fis.internal-api-key:change-me}") private val internalApiKey: String
) {
    private val webClient = WebClient.builder()
        .baseUrl(fisBaseUrl)
        .defaultHeader("X-Internal-Key", internalApiKey)
        .build()

    /**
     * Generates an embedding for the query via the Python intelligence service,
     * then performs a cosine similarity search in pgvector.
     *
     * @param query      Natural language search query (e.g. child summary + urgency context)
     * @param urgencyScore Used to prioritize the limit — higher urgency → more chunks
     * @param limit      Max number of chunks to return (default 5)
     */
    fun search(query: String, urgencyScore: BigDecimal, limit: Int = 5): List<KnowledgeChunk> {
        val embedding = generateEmbedding(query) ?: return emptyList()
        val pgVectorLiteral = embedding.joinToString(",", "[", "]")

        val dynamicLimit = when {
            urgencyScore >= BigDecimal("0.75") -> limit + 2
            urgencyScore <= BigDecimal("0.25") -> maxOf(2, limit - 2)
            else -> limit
        }

        return medicalKnowledgeRepository
            .findTopNByEmbeddingSimilarity(pgVectorLiteral, dynamicLimit)
            .map { KnowledgeChunk(it.id.toString(), it.title, it.content, it.category) }
    }

    /**
     * Generates a 1024-dim embedding by calling the Python intelligence service.
     * The same model (intfloat/multilingual-e5-large) is used for all embeddings.
     */
    fun generateEmbedding(text: String): List<Float>? {
        return try {
            webClient.post()
                .uri("/embed")
                .bodyValue(EmbeddingRequest(text))
                .retrieve()
                .bodyToMono(EmbeddingResponse::class.java)
                .timeout(Duration.ofSeconds(60))
                .block()
                ?.embedding
        } catch (ex: Exception) {
            null
        }
    }
}
