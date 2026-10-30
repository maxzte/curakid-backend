package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.measurements.MeasurementWithReportProjection
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import com.misterio.curakid.core.essential.common.PagedResponse
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.NoSuchElementException
import java.util.UUID

@Service
class MeasurementService(
    private val measurementRepository: MeasurementRepository,
    private val childRepository: ChildRepository,
    private val whoZScoreCalculator: WhoZScoreCalculator,
    private val reportProvider: ReportProvider,
    private val measurementCalculator: MeasurementCalculator,
    private val transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(MeasurementService::class.java)

    fun getMeasurements(childId: UUID, user: User, pageable: Pageable): PagedResponse<MeasurementResponse> {
        val child = requireNotNull(transactionManager.readTx {
            childRepository.findByIdAndUser(childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
        })
        val childIdNonNull = requireNotNull(child.id) { "Child ID must not be null" }
        val page = measurementRepository.findAllByChildIdWithReportIdPaginated(childIdNonNull, pageable)
            .map { MeasurementResponse.from(it) }
        return PagedResponse(
            content = page.content,
            pageNumber = page.number,
            pageSize = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            last = page.isLast
        )
    }

    fun getMeasurements(childId: UUID, user: User): List<MeasurementWithReportProjection> {
        val child = requireNotNull(transactionManager.readTx {
            childRepository.findByIdAndUser(childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
        })
        return getMeasurements(child)
    }

    fun getMeasurements(child: Child): List<MeasurementWithReportProjection> {
        val childId = requireNotNull(child.id) { "Child ID must not be null" }
        return measurementRepository.findAllByChildIdWithReportId(childId)
    }

    fun deleteMeasurement(childId: UUID, measurementId: UUID, user: User) {
        transactionManager.writeTx {
            val child = childRepository.findByIdAndUser(childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
            val measurement = measurementRepository.findById(measurementId)
                .orElseThrow { NoSuchElementException("Measurement not found") }
            if (measurement.child?.id != child.id) {
                throw IllegalArgumentException("Measurement does not belong to this child")
            }
            measurement.deletedAt = java.time.Instant.now()
            measurement.isActive = false
            measurementRepository.save(measurement)
        }
    }

    fun addMeasurement(childId: UUID, user: User, request: AddMeasurementRequest): MeasurementResponse {
        val child = requireNotNull(transactionManager.readTx {
            childRepository.findByIdAndUser(childId, user)
                .orElseThrow { NoSuchElementException("Child not found") }
        })
        return addMeasurement(child, request)
    }

    fun addMeasurement(child: Child, request: AddMeasurementRequest): MeasurementResponse {
        // Phase 1: Read Context
        val (measurement, prevHistory) = requireNotNull(transactionManager.readTx {
            val byId = measurementRepository.findById(request.id).orElse(null)
            val byDate = measurementRepository.findByChildAndMeasurementDate(child, request.measurementDate).orElse(null)

            val existing = if (byId != null) {
                if (byId.child?.id != child.id) {
                    throw IllegalArgumentException("Measurement does not belong to this child")
                }
                byId
            } else {
                byDate
            }

            if (byId != null && byId.measurementDate != request.measurementDate && byDate != null && byDate.id != byId.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Measurement for this date already exists with a different ID")
            }

            val m = existing ?: Measurement(
                child = child,
                measurementDate = request.measurementDate
            ).apply { setId(request.id) }

            if (existing != null && existing.measurementDate != request.measurementDate) {
                m.measurementDate = request.measurementDate
            }

            val measurementLocalDate = LocalDate.ofInstant(request.measurementDate, ZoneOffset.UTC)
            val ageDays = ChronoUnit.DAYS.between(child.birthDate, measurementLocalDate).toInt()
            val lookbackDays = AgeWindows.maxLookbackDays(ageDays)
            val lookbackDate = request.measurementDate.minus(lookbackDays, ChronoUnit.DAYS)
            val history = measurementRepository.findByChildAndMeasurementDateBetweenOrderByMeasurementDateDesc(
                child, lookbackDate, request.measurementDate.minus(1, ChronoUnit.MILLIS)
            )

            Pair(m, history)
        })

        // Phase 2: Pure Domain Processing (Calculations without DB connection)
        request.weightKg?.let { measurement.weightKg = it }
        request.heightCm?.let { measurement.heightCm = it }
        request.headCircCm?.let { measurement.headCircCm = it }

        val measurementLocalDate = LocalDate.ofInstant(request.measurementDate, ZoneOffset.UTC)

        measurement.weightKg?.let { weight ->
            measurement.wfaZscore = whoZScoreCalculator.calculateWfaZscore(
                weight, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.wfaPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.wfaZscore)
            measurement.wfaDeltaZ = measurementCalculator.calculateDelta(
                prevHistory,
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
                prevHistory,
                measurement.hfaZscore,
                Measurement::hfaZscore
            )
        }
        measurement.headCircCm?.let { head ->
            measurement.hcfaZscore = whoZScoreCalculator.calculateHcfaZscore(
                head, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.hcfaPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.hcfaZscore)
            measurement.hcfaDeltaZ = measurementCalculator.calculateDelta(
                prevHistory,
                measurement.hcfaZscore,
                Measurement::hcfaZscore
            )
        }

        val weightVal = measurement.weightKg
        val heightVal = measurement.heightCm
        if (weightVal != null && heightVal != null) {
            val ageDays = ChronoUnit.DAYS.between(child.birthDate, measurementLocalDate).toInt()
            measurement.bmiZscore = whoZScoreCalculator.calculateBmiZscore(
                weightVal, heightVal, child.birthDate, child.gender, measurementLocalDate
            )
            measurement.bmiPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.bmiZscore)
            measurement.bmiDeltaZ = measurementCalculator.calculateDelta(
                prevHistory,
                measurement.bmiZscore,
                Measurement::bmiZscore
            )
            if (ageDays < 730) {
                measurement.wfhZscore = whoZScoreCalculator.calculateWfhZscore(
                    weightVal, heightVal, child.birthDate, child.gender, measurementLocalDate
                )
                measurement.wfhPercentile = whoZScoreCalculator.zScoreToPercentile(measurement.wfhZscore)
                measurement.wfhDeltaZ = measurementCalculator.calculateDelta(
                    prevHistory,
                    measurement.wfhZscore,
                    Measurement::wfhZscore
                )
            }
        }

        // Phase 3: Write Transaction
        return requireNotNull(transactionManager.writeTx {
            val saved = measurementRepository.save(measurement)
            measurementRepository.flush()

            val historyForVelocity = measurementRepository
                .findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(child, saved.measurementDate)

            val velocityContext = VelocityContext(
                wfaVelocity = measurementCalculator.calculateVelocity(
                    historyForVelocity, saved.wfaZscore, saved.measurementDate, Measurement::wfaZscore
                ),
                hfaVelocity = measurementCalculator.calculateVelocity(
                    historyForVelocity, saved.hfaZscore, saved.measurementDate, Measurement::hfaZscore
                ),
                bmiVelocity = measurementCalculator.calculateVelocity(
                    historyForVelocity, saved.bmiZscore, saved.measurementDate, Measurement::bmiZscore
                ),
                hcfaVelocity = measurementCalculator.calculateVelocity(
                    historyForVelocity, saved.hcfaZscore, saved.measurementDate, Measurement::hcfaZscore
                ),
                wfhVelocity = measurementCalculator.calculateVelocity(
                    historyForVelocity, saved.wfhZscore, saved.measurementDate, Measurement::wfhZscore
                ),
                daysSinceLast = historyForVelocity.firstOrNull()?.let {
                    ChronoUnit.DAYS.between(it.measurementDate, saved.measurementDate)
                }
            )

            val reportId = checkTriggers(saved, velocityContext)?.let {
                val childId = requireNotNull(child.id) { "Child ID must not be null" }
                val measurementId = requireNotNull(saved.id) { "Saved measurement ID must not be null" }
                reportProvider.generateEmergencyReport(childId, measurementId)
            }

            MeasurementResponse.from(saved, reportId)
        })
    }

    object AgeWindows {
        fun maxLookbackDays(ageDays: Int): Long = when {
            ageDays < 14 -> 7L
            ageDays < 90 -> 30L
            ageDays < 365 -> 60L
            else -> 120L
        }
    }

    private fun checkTriggers(m: Measurement, v: VelocityContext): TriggerContext? {
        val triggers = buildList {
            m.wfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.WFA_OUT_OF_RANGE) }
            m.hfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.HFA_OUT_OF_RANGE) }
            m.hcfaZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.HCFA_OUT_OF_RANGE) }
            m.bmiZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.BMI_OUT_OF_RANGE) }
            m.wfhZscore?.let { if (it.abs() > BigDecimal("2")) add(Trigger.WFH_OUT_OF_RANGE) }

            m.wfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.WFA_RAPID_CHANGE) }
            m.hfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.HFA_RAPID_CHANGE) }
            m.bmiDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.BMI_RAPID_CHANGE) }
            m.hcfaDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.HCFA_RAPID_CHANGE) }
            m.wfhDeltaZ?.let { if (it.abs() > BigDecimal("1")) add(Trigger.WFH_RAPID_CHANGE) }

            v.wfaVelocity?.let { if (it < BigDecimal("-0.3")) add(Trigger.WFA_PERSISTENT_DECLINE) }
            v.hfaVelocity?.let { if (it < BigDecimal("-0.2")) add(Trigger.HFA_STUNTING_RISK) }
            v.bmiVelocity?.let { if (it.abs() > BigDecimal("0.3")) add(Trigger.BMI_VELOCITY_ISSUE) }
            v.hcfaVelocity?.let { if (it.abs() > BigDecimal("0.2")) add(Trigger.HCFA_VELOCITY_ISSUE) }
            v.wfhVelocity?.let { if (it.abs() > BigDecimal("0.3")) add(Trigger.WFH_VELOCITY_ISSUE) }

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
