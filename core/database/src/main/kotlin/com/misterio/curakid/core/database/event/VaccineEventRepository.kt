package com.misterio.curakid.core.database.event

import com.misterio.curakid.core.database.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface VaccineEventRepository : JpaRepository<VaccineEvent, UUID> {
    fun findAllByChild(child: Child): List<VaccineEvent>
}
