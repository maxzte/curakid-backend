package com.misterio.curakid.core.database.exercise

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ExerciseRecommendationRepository : JpaRepository<ExerciseRecommendation, UUID>
