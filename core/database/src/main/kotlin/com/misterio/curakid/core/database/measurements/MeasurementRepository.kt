package com.misterio.curakid.core.database.measurements

import com.misterio.curakid.core.database.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant
import java.util.*

interface MeasurementWithReportProjection {
    val id: UUID
    val childId: UUID
    val measurementDate: Instant
    val aiReportId: UUID?

    val weightKg: BigDecimal?
    val heightCm: BigDecimal?
    val headCircCm: BigDecimal?

    val wfaZscore: BigDecimal?
    val hfaZscore: BigDecimal?
    val bmiZscore: BigDecimal?
    val wfhZscore: BigDecimal?
    val hcfaZscore: BigDecimal?

    val wfaPercentile: BigDecimal?
    val hfaPercentile: BigDecimal?
    val bmiPercentile: BigDecimal?
    val hcfaPercentile: BigDecimal?
    val wfhPercentile: BigDecimal?
}

interface MeasurementRepository : JpaRepository<Measurement, UUID> {

    @Query("""
        SELECT 
            m.id as id, 
            m.child.id as childId, 
            m.weightKg as weightKg, 
            m.heightCm as heightCm, 
            m.headCircCm as headCircCm, 
            m.wfaZscore as wfaZscore,
            m.hfaZscore as hfaZscore,
            m.bmiZscore as bmiZscore,
            m.hcfaZscore as hcfaZscore,
            m.wfhZscore as wfhZscore,
            m.wfaPercentile as wfaPercentile,
            m.hfaPercentile as hfaPercentile,
            m.bmiPercentile as bmiPercentile,
            m.hcfaPercentile as hcfaPercentile,
            m.wfhPercentile as wfhPercentile,
            m.measurementDate as measurementDate, 
            r.id as aiReportId
        FROM Measurement m
        LEFT JOIN AiReport r ON r.triggerMeasurement = m
        WHERE m.child.id = :childId
        ORDER BY m.measurementDate ASC
    """)
    fun findAllByChildIdWithReportId(@Param("childId") childId: UUID): List<MeasurementWithReportProjection>

    fun findAllByChildOrderByMeasurementDateAsc(child: Child): List<Measurement>
    fun findFirstByChildOrderByMeasurementDateDesc(child: Child): Optional<Measurement>
    fun findTop5ByChildOrderByMeasurementDateDesc(child: Child): List<Measurement>
    fun findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
        child: Child,
        lookbackDate: Instant,
        before: Instant
    ): List<Measurement>
}
