package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.measurements.MeasurementWithReportProjection
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Service
class MeasurementService(
    private val measurementRepository: MeasurementRepository,
    private val whoZScoreCalculator: WhoZScoreCalculator,
    private val reportProvider: ReportProvider,
) {
    private val log = LoggerFactory.getLogger(MeasurementService::class.java)

    fun getMeasurements(child: Child): List<MeasurementWithReportProjection> {
        return measurementRepository.findAllByChildIdWithReportId(child.id!!)
    }

    @Transactional
    fun addMeasurement(child: Child, request: AddMeasurementRequest): MeasurementResponse {
        val ageDays = ChronoUnit.DAYS.between(child.birthDate, request.measurementDate).toInt()
        val lookbackDays = AgeWindows.maxLookbackDays(ageDays)
        val lookbackDate = request.measurementDate.minusDays(lookbackDays)
        val prev = findPreviousScores(child, request.measurementDate, lookbackDate)

        val measurement = Measurement(
            child = child,
            measurementDate = request.measurementDate
        )

        if (request.weightKg != null) {
            measurement.weightKg = request.weightKg
            measurement.wfaZscore = whoZScoreCalculator.calculateWfaZscore(
                request.weightKg, child.birthDate, child.gender
            )
            measurement.wfaPercentile =
                whoZScoreCalculator.zScoreToPercentile(measurement.wfaZscore)
            measurement.wfaDeltaZ = calcDelta(measurement.wfaZscore, prev.wfaZscore)
        }
        if (request.heightCm != null) {
            measurement.heightCm = request.heightCm
            measurement.hfaZscore = whoZScoreCalculator.calculateHfaZscore(
                request.heightCm, child.birthDate, child.gender
            )
            measurement.hfaPercentile =
                whoZScoreCalculator.zScoreToPercentile(measurement.hfaZscore)
            measurement.hfaDeltaZ = calcDelta(measurement.hfaZscore, prev.hfaZscore)
        }
        if (request.weightKg != null && request.heightCm != null) {
            measurement.bmiZscore = whoZScoreCalculator.calculateBmiZscore(
                request.weightKg, request.heightCm, child.birthDate, child.gender
            )
            measurement.bmiPercentile =
                whoZScoreCalculator.zScoreToPercentile(measurement.bmiZscore)
            measurement.bmiDeltaZ = calcDelta(measurement.bmiZscore, prev.bmiZscore)

            measurement.wfhZscore = whoZScoreCalculator.calculateWfhZscore(
                request.weightKg, request.heightCm, child.gender
            )
            measurement.wfhPercentile =
                whoZScoreCalculator.zScoreToPercentile(measurement.wfhZscore)
            measurement.wfhDeltaZ = calcDelta(measurement.wfhZscore, prev.wfhZscore)
        }
        if (request.headCircCm != null) {
            measurement.headCircCm = request.headCircCm
            measurement.hcfaZscore = whoZScoreCalculator.calculateHcfaZscore(
                request.headCircCm, child.birthDate, child.gender
            )
            measurement.hcfaPercentile =
                whoZScoreCalculator.zScoreToPercentile(measurement.hcfaZscore)
            measurement.hcfaDeltaZ = calcDelta(measurement.hcfaZscore, prev.hcfaZscore)
        }

        val saved: Measurement = measurementRepository.save(measurement)
        measurementRepository.flush()
        log.debug("saved measurement{}", saved)

        val reportId = checkTriggers(saved)?.let {
            log.debug("we have triggers {}", it)
            reportProvider.generateEmergencyReport(child.id!!, saved.id!!)
        }

        return MeasurementResponse.from(saved, reportId)
    }

    data class AgeWindow(
        val minDays: Int,
        val maxDays: Int,
        val maxLookbackDays: Long  // наскільки старий замір ще вважається актуальним
    )

    // WHO вікові періоди + допустиме вікно для дельти
    object AgeWindows {
        // До 2 тижнів — заміри щодня, дельта актуальна до 7 днів
        // 2 тижні - 3 міс — щотижня, дельта актуальна до 30 днів
        // 3-12 міс — щомісяця, дельта актуальна до 60 днів
        // 1-5 років — кожні 3 міс, дельта актуальна до 120 днів

        fun maxLookbackDays(ageDays: Int): Long = when {
            ageDays < 14 -> 7L
            ageDays < 90 -> 30L
            ageDays < 365 -> 60L
            else -> 120L
        }
    }

    private fun findPreviousScores(
        child: Child,
        before: LocalDateTime,
        lookbackDate: LocalDateTime,
    ): PreviousScores {
        val candidates =
            measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
                child, lookbackDate, before.minusDays(1)
            )
        log.debug("findPrevScores = {}", candidates)
        // Для кожного показника беремо найсвіжіший замір де він є
        return PreviousScores(
            wfaZscore = candidates.firstOrNull { it.wfaZscore != null }?.wfaZscore,
            hfaZscore = candidates.firstOrNull { it.hfaZscore != null }?.hfaZscore,
            bmiZscore = candidates.firstOrNull { it.bmiZscore != null }?.bmiZscore,
            hcfaZscore = candidates.firstOrNull { it.hcfaZscore != null }?.hcfaZscore,
            wfhZscore = candidates.firstOrNull { it.wfhZscore != null }?.wfhZscore,
        )
    }

    data class PreviousScores(
        val wfaZscore: BigDecimal? = null,
        val hfaZscore: BigDecimal? = null,
        val bmiZscore: BigDecimal? = null,
        val hcfaZscore: BigDecimal? = null,
        val wfhZscore: BigDecimal? = null,
    )

    private fun calcDelta(current: BigDecimal?, previous: BigDecimal?): BigDecimal? {
        if (current == null || previous == null) return null
        return (current - previous).setScale(2, RoundingMode.HALF_UP)
    }

    private fun checkTriggers(m: Measurement): TriggerContext? {
        val triggers = buildList {
            m.wfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.WFA_OUT_OF_RANGE) }
            m.hfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.HFA_OUT_OF_RANGE) }
            m.hcfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.HCFA_OUT_OF_RANGE) }
            m.bmiZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.BMI_OUT_OF_RANGE) }
            m.wfhZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.WFH_OUT_OF_RANGE) }
            m.wfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.WFA_RAPID_CHANGE) }
            m.hfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.HFA_RAPID_CHANGE) }
            m.hcfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.HCFA_RAPID_CHANGE) }
            m.bmiDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.BMI_RAPID_CHANGE) }
            m.wfhDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.WFH_RAPID_CHANGE) }
        }
        return if (triggers.isEmpty()) null else TriggerContext(triggers)
    }

    enum class Trigger {
        WFA_OUT_OF_RANGE, HFA_OUT_OF_RANGE, HCFA_OUT_OF_RANGE, BMI_OUT_OF_RANGE, WFH_OUT_OF_RANGE,
        WFA_RAPID_CHANGE, HFA_RAPID_CHANGE, HCFA_RAPID_CHANGE, BMI_RAPID_CHANGE, WFH_RAPID_CHANGE
    }

    data class TriggerContext(val triggers: List<Trigger>)

}
