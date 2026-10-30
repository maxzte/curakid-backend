package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.assessments.AssessmentAnswer
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import com.misterio.curakid.core.database.question.Question
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Handles dynamic skipping: when a child demonstrates a "Yes" (100%) answer,
 * all of its prerequisites are automatically marked as achieved.
 */
@Component
class PrerequisiteAutoFiller(
    private val assessmentRepository: SkillAssessmentRepository
) {

    fun autoFill(
        child: Child,
        currentPeriod: LifePeriod,
        currentAssessment: SkillAssessment,
        question: Question?,
        timestamp: Instant,
        savedAnswers: MutableList<AssessmentAnswer>,
        globalHistoryIds: Set<UUID>
    ) {
        var currentPrereq = question?.prerequisite
        var depth = 0
        val maxDepth = 10 // Safety limit for recursion

        while (currentPrereq != null && depth < maxDepth) {
            val prereq = currentPrereq
            val prereqPeriod = prereq.lifePeriod ?: currentPeriod
            val isCurrentPeriod = prereqPeriod.id == currentPeriod.id

            val isAnsweredLocally = isCurrentPeriod && currentAssessment.answers.any { it.question?.id == prereq.id }
            val isAnsweredGlobally = globalHistoryIds.contains(prereq.id)

            if (!isAnsweredLocally && !isAnsweredGlobally) {
                // Find "Yes" option for this prerequisite
                val yesOption = prereq.options.find { it.value >= 100.0 }
                if (yesOption != null) {
                    val targetAssessment = if (isCurrentPeriod) {
                        currentAssessment
                    } else {
                        assessmentRepository.findByChildAndDomainAndLifePeriod(
                            child,
                            prereq.domain,
                            prereqPeriod
                        ).orElseGet {
                            SkillAssessment(
                                child = child,
                                domain = prereq.domain,
                                lifePeriod = prereqPeriod,
                                assessmentDate = timestamp
                            )
                        }
                    }

                    // A cross-period prerequisite may have been auto-filled
                    // earlier in the same request; the global history snapshot
                    // is stale in that case, so check the target assessment too.
                    val alreadyAnsweredInTarget = targetAssessment.answers.any { it.question?.id == prereq.id }
                    if (!alreadyAnsweredInTarget) {
                        val autoAnswer = AssessmentAnswer(
                            assessment = targetAssessment,
                            question = prereq,
                            selectedOption = yesOption,
                            answeredAt = timestamp
                        )
                        targetAssessment.answers.add(autoAnswer)
                        if (!isCurrentPeriod) {
                            assessmentRepository.save(targetAssessment)
                        }
                        savedAnswers.add(autoAnswer) // Include in final response and validation
                    }
                }
            }
            currentPrereq = prereq.prerequisite
            depth++
        }
    }
}
