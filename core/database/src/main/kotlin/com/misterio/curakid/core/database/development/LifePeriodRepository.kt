package com.misterio.curakid.core.database.development

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

    @Query(
        "SELECT lp FROM LifePeriod lp " +
        "WHERE :ageMonths >= lp.minMonths AND :ageMonths < lp.maxMonths"
    )
    fun findFirstByAgeMonths(ageMonths: Int): Optional<LifePeriod>

    @Query("SELECT COUNT(sa) > 0 FROM SkillAssessment sa WHERE sa.lifePeriod.id = :periodId")
    fun hasAssessments(periodId: UUID): Boolean

    @Query("SELECT COUNT(ps) > 0 FROM PeriodSummary ps WHERE ps.lifePeriod.id = :periodId")
    fun hasSummaries(periodId: UUID): Boolean

}
