package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.assessments.AssessmentAnswer
import com.misterio.curakid.core.database.assessments.SkillAssessment
import com.misterio.curakid.core.database.question.QuestionRepository
import org.springframework.stereotype.Component

/**
 * Checks that the answers a user just submitted do not contradict the
 * prerequisite graph (e.g. marking a skill as learned while its prerequisite
 * is marked as not learned).
 */
@Component
class LogicalConsistencyValidator(
    private val questionRepository: QuestionRepository
) {

    fun validate(
        allAssessments: List<SkillAssessment>,
        newAnswers: List<AssessmentAnswer>
    ): List<String> {
        val warnings = mutableListOf<String>()

        // Contextual historical map
        val allAnswers = allAssessments.flatMap { it.answers }
            .mapNotNull { ans -> ans.question?.id?.let { qId -> qId to ans } }
            .toMap()

        val noAnswerQuestions = newAnswers
            .filter { (it.selectedOption?.value ?: 0.0) < 100.0 }
            .mapNotNull { it.question }

        val dependentQuestionsMap = if (noAnswerQuestions.isNotEmpty()) {
            questionRepository.findAllByPrerequisiteIn(noAnswerQuestions)
                .groupBy { it.prerequisite?.id }
        } else emptyMap()

        newAnswers.forEach { answer ->
            val question = answer.question ?: return@forEach
            val selectedValue = answer.selectedOption?.value ?: return@forEach

            // 1. Check if "Yes" (100) has a "No" (0) prerequisite
            if (selectedValue >= 100.0) {
                val prereq = question.prerequisite
                if (prereq != null) {
                    val prereqAnswer = allAnswers[prereq.id]
                    if (prereqAnswer != null && (prereqAnswer.selectedOption?.value ?: 0.0) < 100.0) {
                        warnings.add("Схоже, сталася помилка. Навичка '${question.text}' зазвичай з'являється після того, як дитина навчиться '${prereq.text}'.")
                    }
                }
            }

            // 2. Check if "No" (0) is a prerequisite for some other "Yes" skill already in the system
            if (selectedValue < 100.0) {
                val dependentQuestions = dependentQuestionsMap[question.id] ?: emptyList()
                dependentQuestions.forEach { depQ ->
                    val depAnswer = allAnswers[depQ.id]
                    if (depAnswer != null && (depAnswer.selectedOption?.value ?: 0.0) >= 100.0) {
                        warnings.add("Ви вказали, що дитина ще не опанувала '${question.text}', хоча раніше було відмічено успіх у '${depQ.text}'. Навичка '${depQ.text}' базується на '${question.text}'.")
                    }
                }
            }
        }

        return warnings.distinct()
    }
}
