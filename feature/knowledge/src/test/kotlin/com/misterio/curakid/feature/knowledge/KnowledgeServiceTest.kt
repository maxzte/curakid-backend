package com.misterio.curakid.feature.knowledge

import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository.MedicalKnowledgeProjection
import com.misterio.curakid.core.essential.common.analysis.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class KnowledgeServiceTest {

    private lateinit var medicalKnowledgeRepository: MedicalKnowledgeRepository
    private lateinit var embeddingProvider: EmbeddingProvider
    private lateinit var intelligenceProvider: IntelligenceProvider
    private lateinit var knowledgeService: KnowledgeService

    @BeforeEach
    fun setUp() {
        medicalKnowledgeRepository = mock()
        embeddingProvider = mock()
        intelligenceProvider = mock()
        knowledgeService = KnowledgeService(medicalKnowledgeRepository, embeddingProvider, intelligenceProvider)

        // Default mock behavior for rerankChunks: passthrough
        whenever(intelligenceProvider.rerankChunks(any())).thenAnswer { invocation ->
            val req = invocation.getArgument<RerankRequestDto>(0)
            RerankResponseDto(
                chunks = req.chunks.map {
                    KnowledgeChunkDto(
                        title = it.title,
                        content = it.content,
                        relevanceScore = 0.8,
                        isSafeAndApplicable = true
                    )
                }
            )
        }
    }

    private fun createMockProjection(id: UUID, title: String, content: String, distance: Double): MedicalKnowledgeProjection {
        val proj = mock<MedicalKnowledgeProjection>()
        whenever(proj.id).thenReturn(id)
        whenever(proj.title).thenReturn(title)
        whenever(proj.content).thenReturn(content)
        whenever(proj.distance).thenReturn(distance)
        return proj
    }

    @Test
    fun `search should return HIGH confidence when distance is small`() {
        val query = "test query"
        val urgency = BigDecimal("0.3")
        val id = UUID.randomUUID()
        val proj = createMockProjection(id, "Title", "Content", 0.1)

        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(listOf(0.1f, 0.2f))
        whenever(medicalKnowledgeRepository.findTopNByEmbeddingSimilarity(any(), any())).thenReturn(listOf(proj))
        whenever(medicalKnowledgeRepository.findTopNByFullTextSearch(any(), any())).thenReturn(emptyList())

        val result = knowledgeService.search(query, urgency, null)

        assertEquals(SearchConfidence.HIGH, result.confidence)
        assertEquals(1, result.chunks.size)
        assertEquals("Title", result.chunks[0].title)
    }

    @Test
    fun `search should return CAUTION confidence when distance is medium`() {
        val query = "test query"
        val urgency = BigDecimal("0.3")
        val id = UUID.randomUUID()
        val proj = createMockProjection(id, "Title", "Content", 0.3)

        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(listOf(0.1f, 0.2f))
        whenever(medicalKnowledgeRepository.findTopNByEmbeddingSimilarity(any(), any())).thenReturn(listOf(proj))
        whenever(medicalKnowledgeRepository.findTopNByFullTextSearch(any(), any())).thenReturn(emptyList())

        val result = knowledgeService.search(query, urgency, null)

        assertEquals(SearchConfidence.CAUTION, result.confidence)
    }

    @Test
    fun `search should filter out chunks flagged as unsafe by System 1`() {
        val query = "харчування"
        val urgency = BigDecimal("0.3")
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val proj1 = createMockProjection(id1, "Безпечний протокол", "Загальні норми", 0.15)
        val proj2 = createMockProjection(id2, "Небезпечний алерген", "Вживати молоко", 0.12)

        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(listOf(0.1f, 0.2f))
        whenever(medicalKnowledgeRepository.findTopNByEmbeddingSimilarity(any(), any())).thenReturn(listOf(proj1, proj2))
        whenever(medicalKnowledgeRepository.findTopNWithAgeFilter(any(), any(), any())).thenReturn(listOf(proj1, proj2))
        whenever(medicalKnowledgeRepository.findTopNByFullTextSearch(any(), any())).thenReturn(emptyList())

        // System 1 flags the second chunk as unsafe
        whenever(intelligenceProvider.rerankChunks(any())).thenReturn(
            RerankResponseDto(
                chunks = listOf(
                    KnowledgeChunkDto(title = "Безпечний протокол", content = "Загальні норми", relevanceScore = 0.85, isSafeAndApplicable = true),
                    KnowledgeChunkDto(title = "Небезпечний алерген", content = "Вживати молоко", relevanceScore = 0.20, isSafeAndApplicable = false)
                )
            )
        )

        val anamnesis = ChildAnamnesisContext(ageMonths = 12, allergies = listOf("молоко"))
        val result = knowledgeService.search(query, urgency, 12, anamnesis)

        assertEquals(1, result.chunks.size)
        assertEquals("Безпечний протокол", result.chunks[0].title)
    }

    @Test
    fun `search should return FALLBACK confidence when distance is large`() {
        val query = "test query"
        val urgency = BigDecimal("0.3")
        val id = UUID.randomUUID()
        val proj = createMockProjection(id, "Title", "Content", 0.5)

        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(listOf(0.1f, 0.2f))
        whenever(medicalKnowledgeRepository.findTopNByEmbeddingSimilarity(any(), any())).thenReturn(listOf(proj))
        whenever(medicalKnowledgeRepository.findTopNByFullTextSearch(any(), any())).thenReturn(emptyList())

        val result = knowledgeService.search(query, urgency, null)

        assertEquals(SearchConfidence.FALLBACK, result.confidence)
        assertEquals("Загальні рекомендації з безпеки та розвитку (ВООЗ)", result.chunks[0].title)
    }

    @Test
    fun `search should return FALLBACK when no results found`() {
        val query = "test query"
        val urgency = BigDecimal("0.3")

        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(listOf(0.1f, 0.2f))
        whenever(medicalKnowledgeRepository.findTopNByEmbeddingSimilarity(any(), any())).thenReturn(emptyList<MedicalKnowledgeProjection>())
        whenever(medicalKnowledgeRepository.findTopNByFullTextSearch(any(), any())).thenReturn(emptyList<MedicalKnowledgeProjection>())

        val result = knowledgeService.search(query, urgency, null)

        assertEquals(SearchConfidence.FALLBACK, result.confidence)
        assertEquals(1, result.chunks.size)
    }
}
