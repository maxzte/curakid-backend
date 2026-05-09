package com.misterio.curakid.vaccine

import com.misterio.curakid.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface VaccineRepository : JpaRepository<Vaccine, UUID> {
    fun findAllByChildIdOrderByScheduledAgeMonthsAsc(childId: UUID): List<Vaccine>
    fun findAllByChild(child: Child): List<Vaccine>
}
