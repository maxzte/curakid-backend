package com.misterio.curakid.core.database.reports

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.*

interface PeriodSummaryRepository : JpaRepository<PeriodSummary, UUID> {

    fun findTopByChildOrderByCreatedAtDesc(child: Child): Optional<PeriodSummary>

    fun findAllByChildOrderByCreatedAtDesc(child: Child): List<PeriodSummary>

    fun findByChildAndLifePeriod(child: Child, lifePeriod: LifePeriod): Optional<PeriodSummary>

    fun findByIdAndChildUser(summaryId: UUID, userId: UUID?): Optional<PeriodSummary>

}

interface AiReportRepository : JpaRepository<AiReport, UUID> {

    fun findAllByChildOrderByCreatedAtDesc(child: Child): List<AiReport>

    fun findByPeriodSummary(periodSummary: PeriodSummary): Optional<AiReport>

    fun findByIdAndChildUserId(reportId: UUID, userId: UUID): Optional<AiReport>

    fun findByIdAndChildUser(reportId: UUID, user: User): Optional<AiReport> = findByIdAndChildUserId(reportId, user.id)

    @Modifying
    @Query("UPDATE AiReport r SET r.faithfulnessScore = :faithfulness, r.relevanceScore = :relevance WHERE r.id = :id")
    fun updateEvaluationScores(id: UUID, faithfulness: Double, relevance: Double): Int

}
