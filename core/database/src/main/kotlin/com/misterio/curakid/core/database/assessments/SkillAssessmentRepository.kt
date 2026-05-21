package com.misterio.curakid.core.database.assessments

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SkillAssessmentRepository : JpaRepository<SkillAssessment, UUID> {
    fun findAllByChildOrderByAssessmentDateDesc(child: Child): List<SkillAssessment>
    fun findAllByChildAndDomain(child: Child, domain: AssessmentDomain): List<SkillAssessment>
    fun findByIdAndChild(id: UUID, child: Child): Optional<SkillAssessment>
    fun findByChildAndDomainAndLifePeriod(child: Child, domain: AssessmentDomain, lifePeriod: LifePeriod): Optional<SkillAssessment>
}
