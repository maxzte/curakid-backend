package com.misterio.curakid.measurements

import com.misterio.curakid.children.Child
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface MeasurementRepository : JpaRepository<Measurement, UUID> {
    fun findAllByChildOrderByMeasurementDateAsc(child: Child): List<Measurement>
}
