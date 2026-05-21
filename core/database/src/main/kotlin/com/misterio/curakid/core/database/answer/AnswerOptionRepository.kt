package com.misterio.curakid.core.database.answer

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*


interface AnswerOptionRepository : JpaRepository<AnswerOption, UUID>
