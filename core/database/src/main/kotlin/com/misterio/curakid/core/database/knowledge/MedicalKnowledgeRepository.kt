package com.misterio.curakid.core.database.knowledge

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface MedicalKnowledgeRepository : JpaRepository<MedicalKnowledge, UUID> {

    interface MedicalKnowledgeProjection {
        val id: UUID
        val title: String
        val content: String
        val distance: Double
    }

    /**
     * Vector similarity search using pgvector cosine distance.
     * Returns the top-N most relevant chunks for the given embedding.
     * Urgency score can be used to boost category-specific results.
     */
    @Query(
        value = """
            SELECT *, (embedding <=> CAST(:embedding AS vector)) as distance FROM (
                SELECT DISTINCT ON (source_id) * FROM medical_knowledge
                WHERE deleted_at IS NULL
                ORDER BY source_id, embedding <=> CAST(:embedding AS vector)
            ) sub
            ORDER BY sub.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findTopNByEmbeddingSimilarity(
        @Param("embedding") embedding: String,
        @Param("limit") limit: Int
    ): List<MedicalKnowledgeProjection>

    @Query(
        value = """
            SELECT *, (embedding <=> CAST(:embedding AS vector)) as distance FROM (
                SELECT DISTINCT ON (source_id) * FROM medical_knowledge mk
                WHERE mk.deleted_at IS NULL AND (
                    mk.metadata->'age' IS NULL OR 
                    (
                      (mk.metadata->'age'->>'min')::float <= :ageMonths AND 
                      (mk.metadata->'age'->>'max')::float >= :ageMonths
                    )
                )
                ORDER BY mk.source_id, mk.embedding <=> CAST(:embedding AS vector)
            ) sub
            ORDER BY sub.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findTopNWithAgeFilter(
        @Param("embedding") embedding: String,
        @Param("ageMonths") ageMonths: Double,
        @Param("limit") limit: Int
    ): List<MedicalKnowledgeProjection>

    @Query(
        value = """
            SELECT DISTINCT ON (source_id) * FROM medical_knowledge
            WHERE category_id = :categoryId AND deleted_at IS NULL
            ORDER BY source_id, embedding <=> CAST(:embedding AS vector)
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
            SELECT DISTINCT ON (source_id) * FROM medical_knowledge mk
            WHERE mk.deleted_at IS NULL AND (
                (mk.metadata->'zScore'->>'min')::float <= :zScore AND :zScore <= (mk.metadata->'zScore'->>'max')::float
                OR
                (mk.metadata->'bmi'->>'min')::float <= :bmi AND :bmi <= (mk.metadata->'bmi'->>'max')::float
            )
            ORDER BY source_id, embedding <=> CAST(:embedding AS vector)
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

    /**
     * Full-text search using PostgreSQL tsvector.
     * Supports multiple languages (English, Simple for Ukrainian/others).
     * Grouping by source_id to avoid duplicate articles in search results.
     */
    @Query(
        value = """
            SELECT *, 0.0 as distance FROM (
                SELECT DISTINCT ON (source_id) * FROM medical_knowledge
                WHERE (
                    to_tsvector('english', title || ' ' || content) @@ websearch_to_tsquery('english', :query)
                    OR
                    to_tsvector('simple', title || ' ' || content) @@ websearch_to_tsquery('simple', :query)
                ) AND deleted_at IS NULL
                ORDER BY source_id, title
            ) sub
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findTopNByFullTextSearch(
        @Param("query") query: String,
        @Param("limit") limit: Int
    ): List<MedicalKnowledgeProjection>

}
