package com.misterio.curakid.assessments

import com.misterio.curakid.children.Child
import com.misterio.curakid.development.LifePeriodRepository
import com.misterio.curakid.development.QuestionRepository
import com.misterio.curakid.measurements.MeasurementRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class SkillAssessmentServiceTest {

    private lateinit var assessmentRepository: SkillAssessmentRepository
    private lateinit var questionRepository: QuestionRepository
    private lateinit var lifePeriodRepository: LifePeriodRepository
    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var assessmentService: SkillAssessmentService

    @BeforeEach
    fun setUp() {
        assessmentRepository = mock()
        questionRepository = mock()
        lifePeriodRepository = mock()
        measurementRepository = mock()
        assessmentService = SkillAssessmentService(
            assessmentRepository,
            questionRepository,
            lifePeriodRepository,
            measurementRepository
        )
    }

    @Test
    fun `getAssessments should call repository with domain filter if provided`() {
        val child = Child().apply { id = UUID.randomUUID() }
        val domain = AssessmentDomain.MOTOR
        whenever(assessmentRepository.findAllByChildAndDomain(child, domain)).thenReturn(emptyList())

        assessmentService.getAssessments(child, domain)

        verify(assessmentRepository).findAllByChildAndDomain(child, domain)
        verify(assessmentRepository, never()).findAllByChildOrderByAssessmentDateDesc(any())
    }

    @Test
    fun `getAssessments should return all if domain is null`() {
        val child = Child().apply { id = UUID.randomUUID() }
        whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)).thenReturn(emptyList())

        assessmentService.getAssessments(child, null)

        verify(assessmentRepository).findAllByChildOrderByAssessmentDateDesc(child)
    }

    @Test
    fun `saveAssessment should save and return response`() {
        val child = Child().apply { id = UUID.randomUUID() }

        val request = SaveAssessmentRequest(
            domain = AssessmentDomain.SPEECH,
            rawData = mapOf("q1" to "100"),
            assessmentDate = null
        )
        whenever(assessmentRepository.save(any<SkillAssessment>())).thenAnswer {
            (it.arguments[0] as SkillAssessment).apply { id = UUID.randomUUID() }
        }

        val result = assessmentService.saveAssessment(child, request)

        assertEquals(AssessmentDomain.SPEECH, result.domain)
        assertNull(result.score)
        assertNotNull(result.assessmentDate)
        verify(assessmentRepository).save(any())
    }

    @Test
    fun `updateAssessment should merge rawData and keep draft without score`() {
        val child = Child().apply { id = UUID.randomUUID() }
        val assessmentId = UUID.randomUUID()
        val q1Id = UUID.randomUUID()
        val q2Id = UUID.randomUUID()

        val existingAssessment = SkillAssessment(
            child = child,
            domain = AssessmentDomain.SPEECH,
            score = null,
            rawData = mapOf(q1Id.toString() to "100")
        ).apply { id = assessmentId }

        val updateRequest = SaveAssessmentRequest(
            domain = AssessmentDomain.SPEECH,
            rawData = mapOf(q2Id.toString() to "50"),
            assessmentDate = null
        )

        whenever(assessmentRepository.findByIdAndChild(assessmentId, child)).thenReturn(Optional.of(existingAssessment))
        whenever(assessmentRepository.save(any<SkillAssessment>())).thenAnswer { it.arguments[0] }

        val result = assessmentService.updateAssessment(child, assessmentId, updateRequest)

        assertNull(result.score)
        assertEquals(2, result.rawData?.size)
        assertNotNull(result.rawData?.get(q1Id.toString()))
        assertNotNull(result.rawData?.get(q2Id.toString()))
        verify(assessmentRepository).findByIdAndChild(assessmentId, child)
        verify(assessmentRepository).save(existingAssessment)
    }
}
