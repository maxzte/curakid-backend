package com.misterio.curakid.core.database.question

import com.misterio.curakid.core.database.assessments.AssessmentDomain
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface QuestionRepository : JpaRepository<Question, UUID> {
    @Query(
        "SELECT DISTINCT q FROM Question q " +
            "LEFT JOIN FETCH q.options " +
            "WHERE q.id IN :ids AND q.isActive = true"
    )
    fun findAllWithOptionsByIdIn(ids: Collection<UUID>): List<Question>

    @Query(
        "SELECT DISTINCT q FROM Question q " +
            "LEFT JOIN FETCH q.options " +
            "WHERE q.id IN :ids AND q.domain = :domain AND q.isActive = true"
    )
    fun findAllWithOptionsByIdInAndDomain(ids: Collection<UUID>, domain: AssessmentDomain): List<Question>
}
