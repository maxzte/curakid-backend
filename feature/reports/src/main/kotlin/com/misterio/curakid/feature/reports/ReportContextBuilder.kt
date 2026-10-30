package com.misterio.curakid.feature.reports

import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.FuzzyCondition
import com.misterio.curakid.core.essential.common.analysis.FuzzyRule
import com.misterio.curakid.core.essential.sanitizeUserInput
import com.misterio.curakid.core.medical.MeasurementCalculator
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Component
class ReportContextBuilder(
    private val childRepository: ChildRepository,
    private val measurementRepository: MeasurementRepository,
    private val assessmentRepository: SkillAssessmentRepository,
    private val lifePeriodRepository: LifePeriodRepository,
    private val periodSummaryRepository: PeriodSummaryRepository,
    private val fuzzyRuleRepository: FuzzyRuleRepository,
    private val percentileCalculator: WhoPercentileCalculator,
    private val measurementCalculator: MeasurementCalculator,
    private val cacheManager: CacheManager? = null
) {

    @Transactional(readOnly = true)
    fun prepareEmergencyReportContext(childId: UUID, measurementId: UUID): ReportContext? {
        val child = childRepository.findById(childId).getOrNull() ?: return null
        val measurement = measurementRepository.findById(measurementId).getOrNull() ?: return null

        val ageMonths = child.calculateCorrectedAgeMonths()
        val today = LocalDate.now()
        val birthDate = child.birthDate
        val birthDatePlusMonths = birthDate.plusMonths(ageMonths.toLong())
        val ageDays = ChronoUnit.DAYS.between(birthDatePlusMonths, today).toInt()

        val history =
            measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(
                child, measurement.measurementDate
            )

        return ReportContext(
            childId = child.id!!,
            ageMonths = ageMonths,
            ageDays = ageDays,
            childData = buildChildData(child),
            latestMeasurement = buildMeasurementSummary(measurement, history),
            domainScores = null,
            rules = loadFuzzyRules(),
            periodSummaryId = null,
            overrideMeasurementId = measurement.id
        )
    }

    @Transactional(readOnly = true)
    fun preparePeriodReportContext(childId: UUID, periodId: UUID): ReportContext? {
        val child = childRepository.findById(childId).getOrNull() ?: return null
        val periodSummary = periodSummaryRepository.findById(periodId).getOrNull() ?: return null

        val ageMonths = child.calculateCorrectedAgeMonths()
        val today = LocalDate.now()
        val birthDate = child.birthDate
        val birthDatePlusMonths = birthDate.plusMonths(ageMonths.toLong())
        val ageDays = ChronoUnit.DAYS.between(birthDatePlusMonths, today).toInt()

        val latestMeasurement =
            measurementRepository.findFirstByChildOrderByMeasurementDateDesc(child).orElse(null)
        val history = if (latestMeasurement != null) {
            measurementRepository.findTop5ByChildAndMeasurementDateBeforeOrderByMeasurementDateDesc(
                child, latestMeasurement.measurementDate
            )
        } else emptyList()

        val assessments = assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)

        // Regression logic
        val currentLifePeriod = periodSummary.lifePeriod
        val minMonths = currentLifePeriod?.minMonths
        val previousLifePeriod = if (minMonths != null) {
            lifePeriodRepository.findFirstByMaxMonths(minMonths).orElse(null)
        } else null

        val regressions = mutableMapOf<String, Boolean>()

        fun checkRegression(domain: AssessmentDomain, currentScore: BigDecimal?): BigDecimal? {
            if (currentScore == null || previousLifePeriod == null) return currentScore
            val prevScore =
                assessments.find { it.domain == domain && it.lifePeriod?.id == previousLifePeriod.id }?.score
            if (prevScore != null && currentScore < (prevScore.multiply(BigDecimal("0.9")))) {
                regressions[domain.name] = true
            }
            return currentScore
        }

        val scores = DomainScores(
            motorGross = checkRegression(
                AssessmentDomain.MOTOR_GROSS,
                assessments.firstOrNull { it.domain == AssessmentDomain.MOTOR_GROSS && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            motorFine = checkRegression(
                AssessmentDomain.MOTOR_FINE,
                assessments.firstOrNull { it.domain == AssessmentDomain.MOTOR_FINE && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            speech = checkRegression(
                AssessmentDomain.SPEECH,
                assessments.firstOrNull { it.domain == AssessmentDomain.SPEECH && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            cognitive = checkRegression(
                AssessmentDomain.COGNITIVE,
                assessments.firstOrNull { it.domain == AssessmentDomain.COGNITIVE && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            social = checkRegression(
                AssessmentDomain.SOCIAL_EMOTIONAL,
                assessments.firstOrNull { it.domain == AssessmentDomain.SOCIAL_EMOTIONAL && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            selfCare = checkRegression(
                AssessmentDomain.SELF_CARE,
                assessments.firstOrNull { it.domain == AssessmentDomain.SELF_CARE && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            sensory = checkRegression(
                AssessmentDomain.SENSORY,
                assessments.firstOrNull { it.domain == AssessmentDomain.SENSORY && it.lifePeriod?.id == currentLifePeriod?.id }?.score
            ),
            regressions = regressions
        )

        val cid = requireNotNull(child.id) { "Child ID must not be null" }
        return ReportContext(
            childId = cid,
            ageMonths = ageMonths,
            ageDays = ageDays,
            childData = buildChildData(child),
            latestMeasurement = latestMeasurement?.let { buildMeasurementSummary(it, history) },
            domainScores = scores,
            rules = loadFuzzyRules(),
            periodSummaryId = periodSummary.id,
            overrideMeasurementId = null
        )
    }

    internal fun buildMeasurementSummary(
        measurement: Measurement,
        history: List<Measurement>
    ): MeasurementSummary = MeasurementSummary(
        wfaZscore = measurement.wfaZscore,
        hfaZscore = measurement.hfaZscore,
        bmiZscore = measurement.bmiZscore,
        hcfaZscore = measurement.hcfaZscore,
        wfhZscore = measurement.wfhZscore,
        wfaVelocity = measurementCalculator.calculateVelocity(
            history,
            currentValue = measurement.wfaZscore,
            currentDate = measurement.measurementDate,
            metric = { it.wfaZscore },
        ),
        hfaVelocity = measurementCalculator.calculateVelocity(
            history,
            currentValue = measurement.hfaZscore,
            currentDate = measurement.measurementDate,
            metric = { it.hfaZscore },
        ),
        bmiVelocity = measurementCalculator.calculateVelocity(
            history,
            currentValue = measurement.bmiZscore,
            currentDate = measurement.measurementDate,
            metric = { it.bmiZscore },
        ),
        hcfaVelocity = measurementCalculator.calculateVelocity(
            history,
            currentValue = measurement.hcfaZscore,
            currentDate = measurement.measurementDate,
            metric = { it.hcfaZscore }
        ),
        wfhVelocity = measurementCalculator.calculateVelocity(
            history,
            currentValue = measurement.wfhZscore,
            currentDate = measurement.measurementDate,
            metric = { it.wfhZscore }
        ),
        daysSinceLast = history.firstOrNull()?.let {
            ChronoUnit.DAYS.between(it.measurementDate, measurement.measurementDate)
        }
    )

    internal fun buildChildData(child: Child): ChildData = ChildData(
        gender = child.gender,
        isPremature = child.isPremature,
        allergies = child.allergies.map { sanitizeUserInput(it) },
        chronicConditions = child.chronicConditions.map { sanitizeUserInput(it) },
        pastIllnesses = child.pastIllnesses.map { sanitizeUserInput(it) },
        notes = child.notes.map { sanitizeUserInput(it) }
    )

    fun buildFisNorms(context: ReportContext): Map<String, Map<String, Double>> {
        val res = mutableMapOf<String, Map<String, Double>>()
        val zNorms = mapOf(
            "P3" to -3.0, "P15" to -2.0, "P50" to 0.0, "P85" to 2.0, "P97" to 3.0
        )
        res["wfaZscore"] = zNorms
        res["hfaZscore"] = zNorms
        res["bmiZscore"] = zNorms
        res["hcfaZscore"] = zNorms
        res["wfhZscore"] = zNorms
        return res
    }

    fun buildRawNorms(context: ReportContext): Map<String, Map<String, Double>> {
        val gender = context.childData.gender
        val ageDays = (context.ageMonths * 30.44 + context.ageDays).toInt()
        val latest = measurementRepository.findFirstByChildOrderByMeasurementDateDesc(
            childRepository.getReferenceById(context.childId)
        ).orElse(null)

        return percentileCalculator.buildNormsMap(
            gender = gender,
            ageDays = ageDays,
            heightCm = latest?.heightCm?.toDouble(),
            keyPrefix = "WHO"
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadFuzzyRules(): List<FuzzyRule> {
        val cache = cacheManager?.getCache("fuzzyRules")
        val cached = cache?.get("activeRules", List::class.java) as? List<FuzzyRule>
        if (cached != null) {
            return cached
        }

        val rules = fuzzyRuleRepository.findAllByIsActiveTrue().map {
            FuzzyRule(
                domain = it.domain.name,
                conditions = it.conditions.map { c -> FuzzyCondition(c.variable, c.term) },
                consequent = it.consequent
            )
        }
        cache?.put("activeRules", rules)
        return rules
    }
}
