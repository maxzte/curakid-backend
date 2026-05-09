package com.misterio.curakid.development

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*


interface AnswerOptionRepository : JpaRepository<AnswerOption, UUID>
