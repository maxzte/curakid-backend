package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.auth.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class AssessmentControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var assessmentService: SkillAssessmentService
    private val user = User(email = "test@test.com", passwordHash = "hash", name = "Test").apply {
        id = UUID.randomUUID()
    }
    private val auth = UsernamePasswordAuthenticationToken(user, null, emptyList())
    private val childId = UUID.randomUUID()
    private val periodId = UUID.randomUUID()
    private val assessmentId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        assessmentService = mock()
        val controller = AssessmentController(assessmentService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET assessments returns list`() {
        whenever(assessmentService.getAssessmentsByChild(childId, user)).thenReturn(
            listOf(AssessmentResponse(assessmentId, childId, assessmentDate = Instant.now()))
        )

        mockMvc.perform(get("/api/v1/children/$childId/assessments").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].assessmentId").value(assessmentId.toString()))
    }

    @Test
    fun `POST answers returns 201`() {
        whenever(assessmentService.saveAssessmentAnswers(any(), any(), any(), any())).thenReturn(
            SaveAnswersResponse(listOf(AssessmentResponse(assessmentId, childId, assessmentDate = Instant.now())))
        )

        mockMvc.perform(
            post("/api/v1/children/$childId/assessments/$periodId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"answers":[{"questionId":"${UUID.randomUUID()}","selectedOptionId":"${UUID.randomUUID()}"}]}""")
                .principal(auth)
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `PATCH assessment returns updated`() {
        whenever(assessmentService.updateAssessmentAnswers(any(), any(), any(), any())).thenReturn(
            AssessmentResponse(assessmentId, childId, assessmentDate = Instant.now())
        )

        mockMvc.perform(
            patch("/api/v1/children/$childId/assessments/$assessmentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"questionId":"${UUID.randomUUID()}","selectedOptionId":"${UUID.randomUUID()}"}""")
                .principal(auth)
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `POST complete returns 202`() {
        whenever(assessmentService.completePeriodAssessment(any(), any(), any())).thenReturn(
            CompletePeriodAssessmentResponse("P1M", 100, 80, Instant.now(), emptyList())
        )

        mockMvc.perform(
            post("/api/v1/children/$childId/assessments/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"periodCode":"P1M","allowEarlyTransition":false}""")
                .principal(auth)
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.periodCode").value("P1M"))
    }

    @Test
    fun `GET readiness returns status`() {
        whenever(assessmentService.getPeriodReadiness(childId, "P1M", user)).thenReturn(
            PeriodReadinessResponse(true, 100, 80, emptyList())
        )

        mockMvc.perform(
            get("/api/v1/children/$childId/assessments/readiness?periodCode=P1M").principal(auth)
        )
            .andExpect(status().isOk)
    }
}
