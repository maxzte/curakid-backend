package com.misterio.curakid.admin.service

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
import java.util.*

@Service
class AdminKnowledgeService(
    private val knowledgeRepository: MedicalKnowledgeRepository,
    private val categoryRepository: MedicalCategoryRepository,
    private val embeddingProvider: EmbeddingProvider
) {
    @Transactional
    fun uploadArticle(title: String, content: String, categoryId: UUID): MedicalKnowledge {
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { RuntimeException("Category not found") }

        val embedding = embeddingProvider.generateEmbedding(content)
            ?: throw RuntimeException("Failed to generate embedding for the article")
        
        val article = MedicalKnowledge(
            title = title,
            content = content,
            category = category,
            embedding = embedding.toFloatArray()
        )
        return knowledgeRepository.save(article)
    }

    @Transactional
    fun uploadArticleFromFile(title: String, content: ByteArray, filename: String, categoryId: UUID, metadata: Map<String, Any>): MedicalKnowledge {
        val category = categoryRepository.findById(categoryId)
            .orElseThrow { RuntimeException("Category not found") }

        val textContent = if (filename.lowercase().endsWith(".pdf")) {
            extractTextFromPdf(content)
        } else {
            String(content)
        }

        val doc = KnowledgeDocument(textContent, metadata)
        val embedding = embeddingProvider.embed(doc)
            ?: throw RuntimeException("Failed to generate embedding for the document")

        val article = MedicalKnowledge(
            title = title,
            content = textContent,
            category = category,
            embedding = embedding.toFloatArray(),
            metadata = metadata
        )
        return knowledgeRepository.save(article)
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        return try {
            Loader.loadPDF(bytes).use { document ->
                PDFTextStripper().getText(document)
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to extract text from PDF: ${e.message}")
        }
    }

    fun getArticles(): List<MedicalKnowledge> = knowledgeRepository.findAll()

    @Transactional
    fun deleteArticle(id: UUID) = knowledgeRepository.deleteById(id)

    fun getCategories(): List<MedicalCategory> = categoryRepository.findAll()

    @Transactional
    fun createCategory(request: CreateCategoryRequest): MedicalCategory {
        val category = MedicalCategory(
            name = request.name,
            displayName = request.displayName
        )
        return categoryRepository.save(category)
    }
}
