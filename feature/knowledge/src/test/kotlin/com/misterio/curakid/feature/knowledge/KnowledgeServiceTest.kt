package com.misterio.curakid.feature.knowledge

import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository.MedicalKnowledgeProjection
import com.misterio.curakid.core.essential.common.analysis.EmbeddingProvider
import com.misterio.curakid.core.essential.common.analysis.SearchConfidence
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
    private lateinit var knowledgeService: KnowledgeService

    @BeforeEach
    fun setUp() {
        medicalKnowledgeRepository = mock()
        embeddingProvider = mock()
        knowledgeService = KnowledgeService(medicalKnowledgeRepository, embeddingProvider)
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
