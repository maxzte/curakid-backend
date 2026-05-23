package com.misterio.curakid.core.database.development

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface LifePeriodRepository : JpaRepository<LifePeriod, UUID> {

    fun findByCode(code: String): Optional<LifePeriod>

    @Query(
        "SELECT DISTINCT lp FROM LifePeriod lp " +
            "LEFT JOIN FETCH lp.questions q " +
            "WHERE lp.code = :code AND (q IS NULL OR q.isActive = true)"
    )
    fun findByCodeWithQuestions(code: String): Optional<LifePeriod>

    @Query(
        "SELECT DISTINCT lp FROM LifePeriod lp " +
        "LEFT JOIN FETCH lp.questions q " +
        "WHERE (q IS NULL OR q.isActive = true) " +
        "ORDER BY lp.minMonths"
    )
    fun findAllWithQuestions(): List<LifePeriod>
    @Query(
        "SELECT lp FROM LifePeriod lp " +
        "WHERE :ageMonths >= lp.minMonths AND :ageMonths < lp.maxMonths"
    )
    fun findFirstByAgeMonths(ageMonths: Int): Optional<LifePeriod>
}
