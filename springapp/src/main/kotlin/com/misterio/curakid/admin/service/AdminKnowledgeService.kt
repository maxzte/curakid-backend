package com.misterio.curakid.admin.service

import com.misterio.curakid.admin.AdminArticleResponse
import com.misterio.curakid.admin.AdminCategoryResponse
import com.misterio.curakid.admin.CreateCategoryRequest
import com.misterio.curakid.core.essential.common.analysis.EmbeddingProvider
import com.misterio.curakid.core.essential.common.analysis.KnowledgeDocument
import com.misterio.curakid.core.database.knowledge.MedicalKnowledge
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import com.misterio.curakid.core.database.knowledge.MedicalCategory
import com.misterio.curakid.feature.knowledge.MedicalCategoryRepository
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class AdminKnowledgeService(
    private val knowledgeRepository: MedicalKnowledgeRepository,
    private val categoryRepository: MedicalCategoryRepository,
    private val embeddingProvider: EmbeddingProvider
) {
    private fun MedicalCategory.toResponse() = AdminCategoryResponse(
        id = id ?: UUID.randomUUID(),
        name = name,
        displayName = displayName
    )

    private fun MedicalKnowledge.toResponse() = AdminArticleResponse(
        id = id ?: UUID.randomUUID(),
        title = title,
        content = content,
        category = category?.toResponse(),
        metadata = metadata,
        createdAt = createdAt,
        sourceId = sourceId
    )

    fun uploadArticle(title: String, content: String, categoryId: UUID): List<AdminArticleResponse> {
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { NoSuchElementException("Category not found") }

        val sourceId = UUID.randomUUID()
        val chunks = chunkText(content)
        
        // Generate embeddings outside of DB transaction to prevent HikariCP connection starvation
        return chunks.map { chunk ->
            val embedding = embeddingProvider.generateEmbedding(chunk)
                ?: throw IllegalStateException("Failed to generate embedding for the chunk")
            
            val article = MedicalKnowledge(
                title = title,
                content = chunk,
                category = category,
                embedding = embedding.toFloatArray(),
                sourceId = sourceId
            )
            knowledgeRepository.save(article).toResponse()
        }
    }

    fun uploadArticleFromFile(title: String, content: ByteArray, filename: String, categoryId: UUID, metadata: Map<String, Any>): List<AdminArticleResponse> {
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { NoSuchElementException("Category not found") }

        val textContent = if (filename.lowercase().endsWith(".pdf")) {
            extractTextFromPdf(content)
        } else {
            String(content)
        }

        val sourceId = UUID.randomUUID()
        val chunks = chunkText(textContent)

        // Generate embeddings outside of DB transaction
        return chunks.map { chunk ->
            val doc = KnowledgeDocument(chunk, metadata)
            val embedding = embeddingProvider.embed(doc)
                ?: throw IllegalStateException("Failed to generate embedding for the chunk")

            val article = MedicalKnowledge(
                title = title,
                content = chunk,
                category = category,
                embedding = embedding.toFloatArray(),
                metadata = metadata,
                sourceId = sourceId
            )
            knowledgeRepository.save(article).toResponse()
        }
    }

    private fun chunkText(text: String, chunkSize: Int = 1200, overlap: Int = 200): List<String> {
        if (text.length <= chunkSize) return listOf(text)
        
        val chunks = mutableListOf<String>()
        var start = 0
        
        while (start < text.length) {
            var end = start + chunkSize
            if (end >= text.length) {
                chunks.add(text.substring(start))
                break
            }
            
            // Try to find a natural break (newline or space) near the end of the chunk
            val lastNewline = text.lastIndexOf('\n', end)
            if (lastNewline > start + (chunkSize / 2)) {
                end = lastNewline + 1
            } else {
                val lastSpace = text.lastIndexOf(' ', end)
                if (lastSpace > start + (chunkSize / 2)) {
                    end = lastSpace + 1
                }
            }
            
            chunks.add(text.substring(start, end).trim())
            start = end - overlap
            if (start < 0) start = 0
            // Ensure progress
            if (start >= end) start = end + 1
        }
        return chunks
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        return try {
            Loader.loadPDF(bytes).use { document ->
                PDFTextStripper().getText(document)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to extract text from PDF: ${e.message}")
        }
    }

    fun getArticles(): List<AdminArticleResponse> = knowledgeRepository.findAll().map { it.toResponse() }

    @Transactional
    fun deleteArticle(id: UUID) {
        val article = knowledgeRepository.findById(id).orElseThrow {
            NoSuchElementException("Article not found")
        }
        article.deletedAt = Instant.now()
        knowledgeRepository.save(article)
    }

    fun getCategories(): List<AdminCategoryResponse> = categoryRepository.findAll().map { it.toResponse() }

    @Transactional
    fun createCategory(request: CreateCategoryRequest): AdminCategoryResponse {
        val category = MedicalCategory(
            name = request.name,
            displayName = request.displayName
        )
        return categoryRepository.save(category).toResponse()
    }
}
