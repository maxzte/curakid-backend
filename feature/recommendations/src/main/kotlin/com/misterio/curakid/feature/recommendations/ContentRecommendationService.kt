package com.misterio.curakid.feature.recommendations

import com.misterio.curakid.core.database.exercise.ExerciseRecommendation
import com.misterio.curakid.core.database.exercise.ExerciseRecommendationRepository
import com.misterio.curakid.core.database.knowledge.MedicalKnowledge
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import com.misterio.curakid.core.database.measurements.Measurement
import com.misterio.curakid.core.database.measurements.MeasurementRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.feature.recommendations.dto.MedicalKnowledgeDto
import com.misterio.curakid.feature.recommendations.dto.RecommendationResponseDto
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant

@Service
class ContentRecommendationService(
    private val childRepository: ChildRepository,
    private val measurementRepository: MeasurementRepository,
    private val medicalKnowledgeRepository: MedicalKnowledgeRepository,
    private val exerciseRecommendationRepository: ExerciseRecommendationRepository,
    private val embeddingProvider: com.misterio.curakid.common.analysis.EmbeddingProvider,
    private val chatClient: org.springframework.ai.chat.client.ChatClient // reuse existing chat client
) {
    private val log = LoggerFactory.getLogger(ContentRecommendationService::class.java)

    /**
     * Retrieves personalized articles and, if the LLM proposes an exercise, stores a provisional recommendation.
     * The result is cached for 5 minutes (configured in Spring Cache config).
     */
    @Cacheable(value = "recommendations", key = "#childId + '-' + #periodId")
    fun getRecommendations(childId: Long, periodId: Long): RecommendationResponseDto {
        val child = childRepository.findById(childId).orElseThrow { EntityNotFoundException("Child not found") }
        // latest measurement for the child
        val latest: Measurement = measurementRepository.findAllByChildOrderByMeasurementDateAsc(child).lastOrNull()
            ?: throw EntityNotFoundException("No measurements for child")
        val zScore = latest.bmiZscore?.toDouble() ?: 0.0 // fallback to 0 when missing
        val bmi = latest.bmiZscore?.toDouble() ?: 0.0
        // generate embedding for a simple query string (life period name could be fetched elsewhere; use placeholder)
        val query = "recommendations for child ${child.id} period $periodId"
        val embedding = embeddingProvider.generateEmbedding(query) ?: floatArrayOf()
        val embeddingLiteral = embedding.joinToString(separator = ",", prefix = "[", postfix = "]")
        val knowledgeList: List<MedicalKnowledge> = medicalKnowledgeRepository.findRelevant(
            zScore = zScore,
            bmi = bmi,
            embedding = embeddingLiteral,
            limit = 5
        )
        val articlesDto = knowledgeList.map { mk ->
            MedicalKnowledgeDto(
                id = mk.id!!,
                title = mk.title,
                snippet = mk.content.take(200), // simple snippet
                category = mk.category?.name
            )
        }
        // Call LLM via existing chat client – we construct a minimal prompt asking for an exercise suggestion
        val llmPrompt = "Provide a single exercise suggestion for a child with BMI=$bmi and ZScore=$zScore. Return JSON with fields: name, description, durationDays."
        val llmResponse = try {
            chatClient.prompt(llmPrompt).call().content()
        } catch (ex: Exception) {
            log.warn("LLM call failed: {}", ex.message)
            null
        }
        var exerciseId: Long? = null
        if (llmResponse != null) {
            // Very naive JSON extraction – in production use proper parser
            val name = "(?i)\"name\"\s*:\s*\"([^\"]+)\"".toRegex().find(llmResponse)?.groupValues?.getOrNull(1)
            val description = "(?i)\"description\"\s*:\s*\"([^\"]+)\"".toRegex().find(llmResponse)?.groupValues?.getOrNull(1)
            val durationStr = "(?i)\"durationDays\"\s*:\s*(\d+)".toRegex().find(llmResponse)?.groupValues?.getOrNull(1)
            if (name != null && description != null && durationStr != null) {
                val duration = durationStr.toIntOrNull() ?: 7
                val exercise = ExerciseRecommendation(
                    name = name,
                    description = description,
                    durationDays = duration,
                    childId = child.id!!,
                    createdAt = Instant.now()
                )
                val saved = exerciseRecommendationRepository.save(exercise)
                exerciseId = saved.id!!
            }
        }
        return RecommendationResponseDto(articles = articlesDto, exerciseId = exerciseId)
    }

    /** Accepts a provisional exercise and converts it into a calendar event. */
    @CacheEvict(value = "recommendations", allEntries = true)
    fun acceptExercise(exerciseId: Long) {
        val exercise = exerciseRecommendationRepository.findById(exerciseId).orElseThrow { EntityNotFoundException("Exercise not found") }
        // Convert to Event – reuse existing Event entity (simplified).
        val event = com.misterio.curakid.core.database.event.Event(
            title = exercise.name,
            description = exercise.description,
            child = com.misterio.curakid.core.database.children.Child(id = exercise.childId),
            startDate = java.time.LocalDate.now().plusDays(exercise.durationDays.toLong()),
            type = com.misterio.curakid.core.database.event.EventType.USER,
            status = com.misterio.curakid.core.database.event.EventStatus.PLANNED
        )
        // Assuming an EventRepository bean exists; we obtain it via Spring context lazily.
        val eventRepo = org.springframework.context.ApplicationContextProvider.getApplicationContext()
            .getBean(com.misterio.curakid.core.database.event.EventRepository::class.java)
        eventRepo.save(event)
        // Delete provisional recommendation
        exerciseRecommendationRepository.delete(exercise)
    }

    /** Rejects a provisional exercise – simply deletes it. */
    @CacheEvict(value = "recommendations", allEntries = true)
    fun rejectExercise(exerciseId: Long) {
        val exercise = exerciseRecommendationRepository.findById(exerciseId).orElseThrow { EntityNotFoundException("Exercise not found") }
        exerciseRecommendationRepository.delete(exercise)
    }
}
