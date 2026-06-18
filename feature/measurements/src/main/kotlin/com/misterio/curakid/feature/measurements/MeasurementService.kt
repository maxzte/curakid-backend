package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.measurements.MeasurementWithReportProjection
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
class MeasurementService(
    private val measurementRepository: MeasurementRepository,
    private val whoZScoreCalculator: WhoZScoreCalculator,
    private val reportProvider: ReportProvider,
    private val measurementCalculator: MeasurementCalculator,
) {
    private val log = LoggerFactory.getLogger(MeasurementService::class.java)

    fun getMeasurements(child: Child): List<MeasurementWithReportProjection> {
        return measurementRepository.findAllByChildIdWithReportId(child.id!!)
    }

    @Transactional
    fun addMeasurement(child: Child, request: AddMeasurementRequest): MeasurementResponse {
        // 1. Try find by ID first (to support date/value updates of an existing record)
        val byId = measurementRepository.findById(request.id)

        val measurement = if (byId.isPresent) {
            val existing = byId.get()
            log.debug("Found measurement by ID: {}", request.id)
            
            // Check for date conflict if date is being changed
            if (existing.measurementDate != request.measurementDate) {
                val conflict = measurementRepository.findByChildAndMeasurementDate(child, request.measurementDate)
                if (conflict.isPresent && conflict.get().id != existing.id) {
                    throw IllegalArgumentException("Measurement for this date already exists with a different ID")
                }
                existing.measurementDate = request.measurementDate
            }
            existing
        } else {
            // 2. If not found by ID, try find by Child + Date (to merge data for the same timestamp)
            val byDate = measurementRepository
                .findByChildAndMeasurementDate(child, request.measurementDate)
            
            if (byDate.isPresent) {
                log.debug("Found measurement by date: {}", request.measurementDate)
                byDate.get()
            } else {
                log.debug("Creating new measurement with ID: {}", request.id)
                Measurement(
                    child = child,
                    measurementDate = request.measurementDate
                ).apply {
                    setId(request.id) // Use the provided ID for new record
                }
            }
        }

        // Update values if present in request
        request.weightKg?.let { measurement.weightKg = it }
        request.heightCm?.let { measurement.heightCm = it }
        request.headCircCm?.let { measurement.headCircCm = it }

        // Recalculate scores based on final merged state
        val measurementLocalDate = LocalDate.ofInstant(request.measurementDate, ZoneOffset.UTC)
        val ageDays = ChronoUnit.DAYS.between(child.birthDate, measurementLocalDate).toInt()
        val lookbackDays = AgeWindows.maxLookbackDays(ageDays)
        val lookbackDate = request.measurementDate.minus(lookbackDays, ChronoUnit.DAYS)
        val prev = findPreviousScores(child, request.measurementDate, lookbackDate)

        measurement.weightKg?.let { weight ->
            measurement.wfaZscore = whoZScoreCalculator.calculateWfaZscore(
                weight, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.wfaPercentile =
                whoZScoreCalculator.zScoreToPercentile(measurement.wfaZscore)
            measurement.wfaDeltaZ = measurementCalculator.calculateDelta(
                prev.history,
                measurement.wfaZscore,
                Measurement::wfaZscore
            )
        }
        measurement.heightCm?.let { height ->
            measurement.hfaZscore = whoZScoreCalculator.calculateHfaZscore(
                height, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.hfaPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.hfaZscore)
            measurement.hfaDeltaZ = measurementCalculator.calculateDelta(
                prev.history,
                measurement.hfaZscore,
                Measurement::hfaZscore
            )
        }
        val weightVal = measurement.weightKg
        val heightVal = measurement.heightCm
        if (weightVal != null && heightVal != null) {
            measurement.bmiZscore = whoZScoreCalculator.calculateBmiZscore(
                weightVal, heightVal, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.bmiPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.bmiZscore)
            measurement.bmiDeltaZ = measurementCalculator.calculateDelta(
                prev.history,
                measurement.bmiZscore,
                Measurement::bmiZscore
            )

            measurement.wfhZscore = whoZScoreCalculator.calculateWfhZscore(
                weightVal, heightVal, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.wfhPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.wfhZscore)
            measurement.wfhDeltaZ = measurementCalculator.calculateDelta(
                prev.history,
                measurement.wfhZscore,
                Measurement::wfhZscore
            )
        }
        measurement.headCircCm?.let { head ->
            measurement.hcfaZscore = whoZScoreCalculator.calculateHcfaZscore(
                head, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.hcfaPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.hcfaZscore)
            measurement.hcfaDeltaZ = measurementCalculator.calculateDelta(
                prev.history,
                measurement.hcfaZscore,
                Measurement::hcfaZscore
            )
        }

        val saved: Measurement = measurementRepository.save(measurement)
        measurementRepository.flush()
        log.debug("saved measurement{}", saved)

        // Fetch history for velocity (top 5 from the past)
        val historyForVelocity = measurementRepository
            .findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(child, saved.measurementDate)

        val velocityContext = VelocityContext(
            wfaVelocity = measurementCalculator.calculateVelocity(
                historyForVelocity,
                saved.wfaZscore,
                saved.measurementDate,
                Measurement::wfaZscore
            ),
            hfaVelocity = measurementCalculator.calculateVelocity(
                historyForVelocity,
                saved.hfaZscore,
                saved.measurementDate,
                Measurement::hfaZscore
            ),
            bmiVelocity = measurementCalculator.calculateVelocity(
                historyForVelocity,
                saved.bmiZscore,
                saved.measurementDate,
                Measurement::bmiZscore
            ),
            hcfaVelocity = measurementCalculator.calculateVelocity(
                historyForVelocity,
                saved.hcfaZscore,
                saved.measurementDate,
                Measurement::hcfaZscore
            ),
            wfhVelocity = measurementCalculator.calculateVelocity(
                historyForVelocity,
                saved.wfhZscore,
                saved.measurementDate,
                Measurement::wfhZscore
            ),
            daysSinceLast = historyForVelocity.firstOrNull()?.let { 
                ChronoUnit.DAYS.between(it.measurementDate, saved.measurementDate) 
            }
        )

        val reportId = checkTriggers(saved, velocityContext)?.let {
            log.debug("we have triggers {}", it)
            reportProvider.generateEmergencyReport(child.id!!, saved.id!!)
        }

        return MeasurementResponse.from(saved, reportId)
    }

    object AgeWindows {
        fun maxLookbackDays(ageDays: Int): Long = when {
            ageDays < 14 -> 7L
            ageDays < 90 -> 30L
            ageDays < 365 -> 60L
            else -> 120L
        }
    }

    private fun findPreviousScores(
        child: Child,
        before: Instant,
        lookbackDate: Instant,
    ): PreviousScores {
        // Fix: Use before minus 1ms to include measurements within the same day but exclude current one
        val candidates =
            measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
                child, lookbackDate, before.minus(1, ChronoUnit.MILLIS)
            )
        log.debug("findPrevScores = {}", candidates)
        return PreviousScores(
            history = candidates,
            wfaZscore = candidates.firstOrNull { it.wfaZscore != null }?.wfaZscore,
            hfaZscore = candidates.firstOrNull { it.hfaZscore != null }?.hfaZscore,
            bmiZscore = candidates.firstOrNull { it.bmiZscore != null }?.bmiZscore,
            hcfaZscore = candidates.firstOrNull { it.hcfaZscore != null }?.hcfaZscore,
            wfhZscore = candidates.firstOrNull { it.wfhZscore != null }?.wfhZscore,
        )
    }

    data class PreviousScores(
        val history: List<Measurement>,
        val wfaZscore: BigDecimal? = null,
        val hfaZscore: BigDecimal? = null,
        val bmiZscore: BigDecimal? = null,
        val hcfaZscore: BigDecimal? = null,
        val wfhZscore: BigDecimal? = null,
    )

    private fun checkTriggers(m: Measurement, v: VelocityContext): TriggerContext? {
        val triggers = buildList {
            // Out of range (Absolute)
            m.wfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.WFA_OUT_OF_RANGE) }
            m.hfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.HFA_OUT_OF_RANGE) }
            m.hcfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.HCFA_OUT_OF_RANGE) }
            m.bmiZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.BMI_OUT_OF_RANGE) }
            m.wfhZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.WFH_OUT_OF_RANGE) }

            // Rapid Change (Delta between consecutive)
            m.wfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.WFA_RAPID_CHANGE) }
            m.hfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.HFA_RAPID_CHANGE) }
            m.bmiDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.BMI_RAPID_CHANGE) }
            m.hcfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.HCFA_RAPID_CHANGE) }
            m.wfhDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.WFH_RAPID_CHANGE) }

            // Velocity triggers (Monthly trend)
            v.wfaVelocity?.let { if (it < BigDecimal("-0.3")) add(Trigger.WFA_PERSISTENT_DECLINE) }
            v.hfaVelocity?.let { if (it < BigDecimal("-0.2")) add(Trigger.HFA_STUNTING_RISK) }
            v.bmiVelocity?.let { if (it.abs() > BigDecimal("0.3")) add(Trigger.BMI_VELOCITY_ISSUE) }
            v.hcfaVelocity?.let { if (it.abs() > BigDecimal("0.2")) add(Trigger.HCFA_VELOCITY_ISSUE) }
            v.wfhVelocity?.let { if (it.abs() > BigDecimal("0.3")) add(Trigger.WFH_VELOCITY_ISSUE) }

            // Outlier detection (Jump in delta)
            val outlierThreshold = BigDecimal("2.5")
            m.wfaDeltaZ?.let { if (it.abs() > outlierThreshold) add(Trigger.POTENTIAL_OUTLIER) }
            m.hfaDeltaZ?.let { if (it.abs() > outlierThreshold) add(Trigger.POTENTIAL_OUTLIER) }
            m.bmiDeltaZ?.let { if (it.abs() > outlierThreshold) add(Trigger.POTENTIAL_OUTLIER) }
            m.hcfaDeltaZ?.let { if (it.abs() > outlierThreshold) add(Trigger.POTENTIAL_OUTLIER) }
            m.wfhDeltaZ?.let { if (it.abs() > outlierThreshold) add(Trigger.POTENTIAL_OUTLIER) }
        }
        return if (triggers.isEmpty()) null else TriggerContext(triggers)
    }

    enum class Trigger {
        WFA_OUT_OF_RANGE, HFA_OUT_OF_RANGE, HCFA_OUT_OF_RANGE, BMI_OUT_OF_RANGE, WFH_OUT_OF_RANGE,
        WFA_RAPID_CHANGE, HFA_RAPID_CHANGE, HCFA_RAPID_CHANGE, BMI_RAPID_CHANGE, WFH_RAPID_CHANGE,
        WFA_PERSISTENT_DECLINE, HFA_STUNTING_RISK,
        BMI_VELOCITY_ISSUE, HCFA_VELOCITY_ISSUE, WFH_VELOCITY_ISSUE,
        POTENTIAL_OUTLIER
    }

    data class VelocityContext(
        val wfaVelocity: BigDecimal? = null,
        val hfaVelocity: BigDecimal? = null,
        val bmiVelocity: BigDecimal? = null,
        val hcfaVelocity: BigDecimal? = null,
        val wfhVelocity: BigDecimal? = null,
        val daysSinceLast: Long? = null
    )

    data class TriggerContext(val triggers: List<Trigger>)

}
