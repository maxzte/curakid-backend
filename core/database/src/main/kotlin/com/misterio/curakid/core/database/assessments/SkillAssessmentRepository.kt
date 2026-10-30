package com.misterio.curakid.core.database.assessments

import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.development.LifePeriod
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface SkillAssessmentRepository : JpaRepository<SkillAssessment, UUID> {

    @EntityGraph(attributePaths = ["answers", "answers.question", "answers.selectedOption"])
    fun findAllByChildOrderByAssessmentDateDesc(child: Child): List<SkillAssessment>

    @EntityGraph(attributePaths = ["answers", "answers.question", "answers.selectedOption"])
    fun findAllByChildAndDomain(child: Child, domain: AssessmentDomain): List<SkillAssessment>

    @EntityGraph(attributePaths = ["answers", "answers.question", "answers.selectedOption"])
    fun findByIdAndChild(id: UUID, child: Child): Optional<SkillAssessment>

    @EntityGraph(attributePaths = ["answers", "answers.question", "answers.selectedOption"])
    fun findByChildAndDomainAndLifePeriod(child: Child, domain: AssessmentDomain, lifePeriod: LifePeriod): Optional<SkillAssessment>

    @EntityGraph(attributePaths = ["answers", "answers.question", "answers.selectedOption"])
    fun findAllByChildAndLifePeriod(child: Child, lifePeriod: LifePeriod): List<SkillAssessment>

}
