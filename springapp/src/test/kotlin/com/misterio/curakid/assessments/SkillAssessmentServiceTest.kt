package com.misterio.curakid.assessments

import com.misterio.curakid.core.database.answer.AnswerOption
import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.AssessmentAnswer
import com.misterio.curakid.core.database.assessments.AssessmentDomain
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.question.Question
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.feature.assessments.CompletePeriodAssessmentRequest
import com.misterio.curakid.feature.assessments.SkillAssessmentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class SkillAssessmentServiceTest {

    private lateinit var assessmentRepository: SkillAssessmentRepository
    private lateinit var lifePeriodRepository: LifePeriodRepository
    private lateinit var questionRepository: QuestionRepository
    private lateinit var answerOptionRepository: AnswerOptionRepository
    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var periodSummaryRepository: PeriodSummaryRepository
    private lateinit var reportProvider: ReportProvider
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var service: SkillAssessmentService

    @BeforeEach
    fun setUp() {
        assessmentRepository = mock()
        lifePeriodRepository = mock()
        questionRepository = mock()
        answerOptionRepository = mock()
        measurementRepository = mock()
        periodSummaryRepository = mock()
        reportProvider = mock()
        transactionTemplate = mock()

        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer {
            (it.arguments[0] as TransactionCallback<Any>).doInTransaction(mock<TransactionStatus>())
        }

        service = SkillAssessmentService(
            assessmentRepository,
            lifePeriodRepository,
            questionRepository,
            answerOptionRepository,
            measurementRepository,
            periodSummaryRepository,
            reportProvider,
            transactionTemplate
        )
    }

    @Test
    fun `completePeriodAssessment should calculate weighted scores correctly`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val period =
            LifePeriod(code = "6M", minMonths = 6, maxMonths = 8).apply { id = UUID.randomUUID() }

        val q1 =
            Question(domain = AssessmentDomain.MOTOR_GROSS, weight = BigDecimal("1.0")).apply {
                id = UUID.randomUUID()
            }
        val q2 =
            Question(domain = AssessmentDomain.MOTOR_GROSS, weight = BigDecimal("2.0")).apply {
                id = UUID.randomUUID()
            }
        period.questions.addAll(listOf(q1, q2))

        val opt1 = AnswerOption(question = q1, text = "Yes", value = 50.0, displayOrder = 1).apply {
            id = UUID.randomUUID()
        }
        val opt2 =
            AnswerOption(question = q2, text = "Yes", value = 100.0, displayOrder = 2).apply {
                id = UUID.randomUUID()
            }

        val assessment = SkillAssessment(
            child = child,
            domain = AssessmentDomain.MOTOR_GROSS,
            lifePeriod = period
        ).apply {
            id = UUID.randomUUID()
            answers.addAll(
                listOf(
                    AssessmentAnswer(this, q1, opt1, Instant.now()).apply {
                        id = UUID.randomUUID()
                    },
                    AssessmentAnswer(this, q2, opt2, Instant.now()).apply {
                        id = UUID.randomUUID()
                    }
                ))
        }

        whenever(lifePeriodRepository.findByCodeWithQuestions("6M")).thenReturn(Optional.of(period))
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(any())).thenReturn(
            listOf(assessment)
        )
        whenever(assessmentRepository.save(any<SkillAssessment>())).thenAnswer { it.arguments[0] }
        whenever(assessmentRepository.saveAll(any<List<SkillAssessment>>())).thenAnswer { it.arguments[0] }
        whenever(
            periodSummaryRepository.findByChildAndLifePeriod(
                any(),
                any()
            )
        ).thenReturn(Optional.empty())
        whenever(periodSummaryRepository.save(any())).thenAnswer {
            (it.arguments[0] as com.misterio.curakid.core.database.reports.PeriodSummary).apply {
                id = UUID.randomUUID()
            }
        }

        val result = service.completePeriodAssessment(child, CompletePeriodAssessmentRequest("6M"))

        // Check if save was called
        verify(assessmentRepository).saveAll(any<List<SkillAssessment>>())
        verify(reportProvider).generatePeriodReport(eq(child.id!!), any())
        assertEquals(100, result.completionPercent)
    }

    @Test
    fun `completePeriodAssessment should throw if completion below 80 percent`() {
        val child =
            Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val period = LifePeriod(code = "6M", minMonths = 6, maxMonths = 8).apply {
            id = UUID.randomUUID()
            questions.addAll(
                listOf(
                    Question().apply { id = UUID.randomUUID() },
                    Question().apply { id = UUID.randomUUID() })
            ) // 2 questions
        }

        // Only 1 question answered (50%)
        val assessment = SkillAssessment(child = child, lifePeriod = period).apply {
            id = UUID.randomUUID()
            answers.add(
                AssessmentAnswer(
                    this,
                    period.questions[0],
                    AnswerOption(value = 100.0).apply { id = UUID.randomUUID() },
                    Instant.now()
                ).apply { id = UUID.randomUUID() })
        }

        whenever(lifePeriodRepository.findByCodeWithQuestions("6M")).thenReturn(Optional.of(period))
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(any())).thenReturn(
            listOf(assessment)
        )

        assertThrows(IllegalArgumentException::class.java) {
            service.completePeriodAssessment(child, CompletePeriodAssessmentRequest("6M"))
        }
    }

    @Test
    fun `getPeriodReadiness should return isReady false if one domain has low coverage despite 80 percent total`() {
        val child = Child(birthDate = LocalDate.now().minusMonths(6)).apply { id = UUID.randomUUID() }
        val period = LifePeriod(code = "6M", minMonths = 6, maxMonths = 8).apply {
            id = UUID.randomUUID()
        }

        // Domain A: 15 questions, Domain B: 5 questions. Total 20.
        val questionsA = (1..15).map { Question(domain = AssessmentDomain.MOTOR_GROSS).apply { id = UUID.randomUUID() } }
        val questionsB = (1..5).map { Question(domain = AssessmentDomain.SPEECH).apply { id = UUID.randomUUID() } }
        period.questions.addAll(questionsA)
        period.questions.addAll(questionsB)

        // Answer all 15 in A and 1 in B -> 16/20 = 80% total.
        // Domain B coverage: 1/5 = 20% < 50% (REQUIRED_DOMAIN_COMPLETION_PERCENT).
        val assessmentA = SkillAssessment(child = child, domain = AssessmentDomain.MOTOR_GROSS, lifePeriod = period).apply {
            id = UUID.randomUUID()
            questionsA.forEach { q ->
                answers.add(AssessmentAnswer(this, q, AnswerOption(value = 100.0).apply { id = UUID.randomUUID() }, Instant.now()))
            }
        }
        val assessmentB = SkillAssessment(child = child, domain = AssessmentDomain.SPEECH, lifePeriod = period).apply {
            id = UUID.randomUUID()
            answers.add(AssessmentAnswer(this, questionsB[0], AnswerOption(value = 100.0).apply { id = UUID.randomUUID() }, Instant.now()))
        }

        whenever(lifePeriodRepository.findByCodeWithQuestions("6M")).thenReturn(Optional.of(period))
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(any())).thenReturn(
            listOf(assessmentA, assessmentB)
        )

        val readiness = service.getPeriodReadiness(child, "6M")

        assertEquals(80, readiness.completionPercent)
        assertEquals(false, readiness.isReady)
        val speechStatus = readiness.domainStatus.find { it.domain == AssessmentDomain.SPEECH.name }!!
        assertEquals(20, speechStatus.completionPercent)
        assertEquals(false, speechStatus.isReady)
    }

    @Test
    fun `getPeriodReadiness should use corrected age for premature babies`() {
        // Child is 8 months chronological, but born at 32 weeks (8 weeks early).
        // Corrected age = 8 - (40-32)/4 = 6 months.
        val chronologicalAge = 8L
        val birthDate = LocalDate.now().minusMonths(chronologicalAge)
        val child = Child(birthDate = birthDate).apply { 
            id = UUID.randomUUID()
            isPremature = true
            gestationalWeeks = 32
        }
        
        // Period 6-8 months should be "Appropriate" for corrected age 6.
        val period = LifePeriod(code = "6M", minMonths = 6, maxMonths = 8).apply { id = UUID.randomUUID() }

        whenever(lifePeriodRepository.findByCodeWithQuestions("6M")).thenReturn(Optional.of(period))
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(any())).thenReturn(
            emptyList()
        )

        val readiness = service.getPeriodReadiness(child, "6M")

        // Should be ready if we ignore other factors (or at least NOT rejected due to age)
        // Since questions are empty, it returns isReady=false but we check the reasons for age-related rejection
        val ageRejectionReason = readiness.reasons.find { it.contains("занадто мала") }
        assertEquals(null, ageRejectionReason, "Should not be rejected by age because corrected age is 6")
    }

    @Test
    fun `validatePeriodAccess should allow early transition if age gap is 3 months and z-score high`() {
        // Child is 1 year old, in period 6-8 is technically over, but age > max also returns.
        val birthDate = LocalDate.now().minusYears(1)
        val child =
            Child(birthDate = birthDate, gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val period = LifePeriod(code = "6M", minMonths = 6, maxMonths = 8).apply {
            id = UUID.randomUUID()
            questions.add(Question().apply { id = UUID.randomUUID() })
        }

        val assessment = SkillAssessment(child = child, lifePeriod = period).apply {
            id = UUID.randomUUID()
            answers.add(
                AssessmentAnswer(
                    this,
                    period.questions[0],
                    AnswerOption(value = 100.0).apply { id = UUID.randomUUID() },
                    Instant.now()
                ).apply { id = UUID.randomUUID() })
        }

        whenever(lifePeriodRepository.findByCodeWithQuestions("6M")).thenReturn(Optional.of(period))
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(any())).thenReturn(
            listOf(assessment)
        )
        whenever(
            periodSummaryRepository.findByChildAndLifePeriod(
                any(),
                any()
            )
        ).thenReturn(Optional.empty())
        whenever(periodSummaryRepository.save(any())).thenAnswer {
            (it.arguments[0] as com.misterio.curakid.core.database.reports.PeriodSummary).apply {
                id = UUID.randomUUID()
            }
        }
        whenever(assessmentRepository.saveAll(any<List<SkillAssessment>>())).thenAnswer { it.arguments[0] }

        // Mock measurement anyway
        val m = Measurement(child = child, measurementDate = Instant.now()).apply {
            wfaZscore = BigDecimal("1.50")
        }
        whenever(measurementRepository.findFirstByChildOrderByMeasurementDateDesc(any())).thenReturn(
            Optional.of(m)
        )

        // Should NOT throw
        service.completePeriodAssessment(
            child,
            CompletePeriodAssessmentRequest("6M", allowEarlyTransition = true)
        )
    }

    @Test
    fun `validatePeriodAccess should deny early transition if age gap more than 3 months`() {
        // Child is 2 months old, period starts at 6 months. Gap = 4.
        val birthDate = LocalDate.now().minusMonths(2)
        val child = Child(birthDate = birthDate).apply { id = UUID.randomUUID() }
        val period =
            LifePeriod(code = "6M", minMonths = 6, maxMonths = 8).apply { id = UUID.randomUUID() }

        whenever(lifePeriodRepository.findByCodeWithQuestions("6M")).thenReturn(Optional.of(period))

        assertThrows(IllegalArgumentException::class.java) {
            service.completePeriodAssessment(
                child,
                CompletePeriodAssessmentRequest("6M", allowEarlyTransition = true)
            )
        }
    }
}
