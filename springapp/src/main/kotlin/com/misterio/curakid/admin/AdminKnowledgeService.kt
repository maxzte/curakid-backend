package com.misterio.curakid.admin

import com.misterio.curakid.knowledge.KnowledgeService
import com.misterio.curakid.knowledge.MedicalKnowledge
import com.misterio.curakid.knowledge.MedicalKnowledgeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdminKnowledgeService(
    private val knowledgeRepository: MedicalKnowledgeRepository,
    private val knowledgeService: KnowledgeService
) {
    @Transactional
    fun uploadArticle(title: String, content: String, category: String?): MedicalKnowledge {
        // Generate embedding via Python service
        val embedding = knowledgeService.generateEmbedding(content)
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
}
