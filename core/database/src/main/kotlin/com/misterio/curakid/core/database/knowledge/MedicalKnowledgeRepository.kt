package com.misterio.curakid.core.database.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface MedicalKnowledgeRepository : JpaRepository<MedicalKnowledge, UUID> {

    /**
     * Vector similarity search using pgvector cosine distance.
     * Returns the top-N most relevant chunks for the given embedding.
     * Urgency score can be used to boost category-specific results.
     */
    @Query(
        value = """
            SELECT * FROM medical_knowledge
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findTopNByEmbeddingSimilarity(
        @Param("embedding") embedding: String,  // formatted as pgvector literal: '[0.1, 0.2, ...]'
        @Param("limit") limit: Int
    ): List<MedicalKnowledge>

    @Query(
        value = """
            SELECT * FROM medical_knowledge
            WHERE category_id = :categoryId
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findTopNByEmbeddingSimilarityAndCategory(
        @Param("embedding") embedding: String,
        @Param("categoryId") categoryId: UUID,
        @Param("limit") limit: Int
    ): List<MedicalKnowledge>
    @Query(
        value = """
            SELECT * FROM medical_knowledge mk
            WHERE (
                (mk.metadata->'zScore'->>'min')::float <= :zScore AND :zScore <= (mk.metadata->'zScore'->>'max')::float
            ) OR (
                (mk.metadata->'bmi'->>'min')::float <= :bmi AND :bmi <= (mk.metadata->'bmi'->>'max')::float
            )
            ORDER BY embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findRelevant(
        @Param("zScore") zScore: Double,
        @Param("bmi") bmi: Double,
        @Param("embedding") embedding: String,
        @Param("limit") limit: Int
    ): List<MedicalKnowledge>
    
    }
