package com.misterio.curakid.vaccine

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface VaccineTemplateRepository : JpaRepository<VaccineTemplate, UUID> {
}
