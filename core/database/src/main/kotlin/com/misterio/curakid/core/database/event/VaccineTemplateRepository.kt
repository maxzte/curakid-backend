package com.misterio.curakid.core.database.event

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface VaccineTemplateRepository : JpaRepository<VaccineTemplate, UUID>
