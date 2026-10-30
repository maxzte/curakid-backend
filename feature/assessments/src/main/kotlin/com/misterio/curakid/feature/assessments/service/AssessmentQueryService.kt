package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.assessments.SkillAssessmentRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.feature.assessments.AssessmentResponse
import com.misterio.curakid.feature.assessments.toAssessmentResponse
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Read-only access to a child's assessments.
 */
@Component
class AssessmentQueryService(
    private val assessmentRepository: SkillAssessmentRepository
) {

    @Transactional(readOnly = true)
    fun getAssessmentsByChild(child: Child): List<AssessmentResponse> {
        return assessmentRepository.findAllByChildOrderByAssessmentDateDesc(child)
            .map { it.toAssessmentResponse() }
    }
}
