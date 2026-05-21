package com.misterio.curakid.admin

import com.misterio.curakid.common.analysis.EmbeddingProvider
import com.misterio.curakid.core.database.knowledge.MedicalKnowledge
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import com.misterio.curakid.core.database.knowledge.MedicalCategory
import com.misterio.curakid.feature.knowledge.MedicalCategoryRepository
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

        // Generate embedding via Python service (now via EmbeddingProvider interface)
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
