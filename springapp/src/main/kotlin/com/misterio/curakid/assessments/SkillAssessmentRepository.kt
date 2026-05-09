package com.misterio.curakid.assessments

import com.misterio.curakid.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SkillAssessmentRepository : JpaRepository<SkillAssessment, UUID> {
    fun findAllByChildOrderByAssessmentDateDesc(child: Child): List<SkillAssessment>
    fun findAllByChildAndDomain(child: Child, domain: AssessmentDomain): List<SkillAssessment>
    fun findByIdAndChild(id: UUID, child: Child): Optional<SkillAssessment>
}
