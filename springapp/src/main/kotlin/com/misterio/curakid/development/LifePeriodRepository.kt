package com.misterio.curakid.development

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface LifePeriodRepository : JpaRepository<LifePeriod, UUID> {

    fun findByCode(code: String): Optional<LifePeriod>

    @Query(
        "SELECT DISTINCT lp FROM LifePeriod lp " +
            "LEFT JOIN FETCH lp.questions q " +
            "WHERE lp.code = :code"
    )
    fun findByCodeWithQuestions(code: String): Optional<LifePeriod>

    @Query(
        "SELECT DISTINCT lp FROM LifePeriod lp " +
        "LEFT JOIN FETCH lp.questions q " +
        "ORDER BY lp.minMonths"
    )
    fun findAllWithQuestions(): List<LifePeriod>
}
