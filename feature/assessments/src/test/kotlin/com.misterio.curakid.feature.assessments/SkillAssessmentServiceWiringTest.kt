package com.misterio.curakid.feature.assessments

import com.misterio.curakid.core.database.answer.AnswerOptionRepository
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.development.LifePeriodRepository
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.question.QuestionRepository
import com.misterio.curakid.core.database.reports.PeriodSummaryRepository
import com.misterio.curakid.core.essential.common.analysis.ReportProvider
import com.misterio.curakid.feature.assessments.service.AnswerPersistenceService
import com.misterio.curakid.feature.assessments.service.AssessmentQueryService
import com.misterio.curakid.feature.assessments.service.ChildAccessService
import com.misterio.curakid.feature.assessments.service.LogicalConsistencyValidator
import com.misterio.curakid.feature.assessments.service.PeriodAccessValidator
import com.misterio.curakid.feature.assessments.service.PeriodCompletionService
import com.misterio.curakid.feature.assessments.service.PeriodReadinessEvaluator
import com.misterio.curakid.feature.assessments.service.PeriodScoreCalculator
import com.misterio.curakid.feature.assessments.service.PrerequisiteAutoFiller
import com.misterio.curakid.feature.assessments.service.ReadinessCacheEvictor
import com.misterio.curakid.feature.assessments.service.RegistrationAgeCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.cache.CacheManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.Optional
import java.util.UUID

class SkillAssessmentServiceWiringTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(Dependencies::class.java)
        .withUserConfiguration(
            ChildAccessService::class.java,
            AssessmentQueryService::class.java,
            AnswerPersistenceService::class.java,
            PrerequisiteAutoFiller::class.java,
            LogicalConsistencyValidator::class.java,
            ReadinessCacheEvictor::class.java,
            PeriodCompletionService::class.java,
            PeriodReadinessEvaluator::class.java,
            PeriodAccessValidator::class.java,
            RegistrationAgeCalculator::class.java,
            PeriodScoreCalculator::class.java,
            SkillAssessmentService::class.java
        )

    @Test
    fun `Spring can wire the service and its collaborators`() {
        contextRunner.run { context ->
            val service = context.getBean(SkillAssessmentService::class.java)
            assertNotNull(service)

            // ChildAccessService must receive the real ChildRepository bean.
            val childId = UUID.randomUUID()
            val child = Child().apply { id = childId }
            val user = mock<User>()
            val childRepository = context.getBean(ChildRepository::class.java)
            whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.of(child))

            assertEquals(child, service.findChildOrThrow(childId, user))
        }
    }

    @Test
    fun `readiness is cached per child and period`() {
        contextRunner.run { context ->
            val child = Child().apply { id = UUID.randomUUID() }
            val period = LifePeriod(code = "P1", minMonths = 0, maxMonths = 3).apply {
                id = UUID.randomUUID()
            }

            val lifePeriodRepository = context.getBean(LifePeriodRepository::class.java)
            whenever(lifePeriodRepository.findByCodeWithQuestions("P1")).thenReturn(Optional.of(period))
            whenever(lifePeriodRepository.findAllWithQuestions()).thenReturn(listOf(period))

            val assessmentRepository = context.getBean(SkillAssessmentRepository::class.java)
            whenever(assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child))
                .thenReturn(emptyList())

            val evaluator = context.getBean(PeriodReadinessEvaluator::class.java)
            evaluator.getReadiness(child, "P1")
            evaluator.getReadiness(child, "P1")

            verify(lifePeriodRepository, times(1)).findByCodeWithQuestions("P1")
        }
    }

    @Test
    fun `ReadinessCacheEvictor evicts every period of the child but keeps other children`() {
        contextRunner.run { context ->
            val childId = UUID.randomUUID()
            val otherChildId = UUID.randomUUID()
            val cache = requireNotNull(context.getBean(CacheManager::class.java).getCache("assessmentReadiness"))

            cache.put("${childId}_P1", "v1")
            cache.put("${childId}_P2", "v2")
            cache.put("${otherChildId}_P1", "other")

            val periodRepository = context.getBean(LifePeriodRepository::class.java)
            whenever(periodRepository.findAllCodes()).thenReturn(listOf("P1", "P2"))

            context.getBean(ReadinessCacheEvictor::class.java).evict(childId, "P1")

            assertNull(cache.get("${childId}_P1"))
            assertNull(cache.get("${childId}_P2"))
            assertNotNull(cache.get("${otherChildId}_P1"))
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    class Dependencies {

        @Bean
        fun assessmentRepository(): SkillAssessmentRepository = mock()

        @Bean
        fun lifePeriodRepository(): LifePeriodRepository = mock()

        @Bean
        fun questionRepository(): QuestionRepository = mock()

        @Bean
        fun answerOptionRepository(): AnswerOptionRepository = mock()

        @Bean
        fun measurementRepository(): MeasurementRepository = mock()

        @Bean
        fun periodSummaryRepository(): PeriodSummaryRepository = mock()

        @Bean
        fun reportProvider(): ReportProvider = mock()

        @Bean
        fun transactionManager(): PlatformTransactionManager = mock()

        @Bean
        fun childRepository(): ChildRepository = mock()

        @Bean
        fun cacheManager(): CacheManager = ConcurrentMapCacheManager("assessmentReadiness")
    }
}
