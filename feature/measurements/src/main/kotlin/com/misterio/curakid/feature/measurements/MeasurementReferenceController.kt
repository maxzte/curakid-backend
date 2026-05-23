package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.medical.ChartType
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/measurements/norms")
class MeasurementReferenceController(
    private val calculator: WhoPercentileCalculator
) {

    @GetMapping("/{type}/{gender}")
    fun getNorms(
        @PathVariable type: ChartType,
        @PathVariable gender: Gender
    ): ResponseEntity<List<ChartNormPoint>> {
        val map = calculator.getRawMap(type, gender)

        val points = map.entries.sortedBy { it.key }.map { (key, params) ->
            val xValue = if (type == ChartType.WFH) key / 10.0 else key.toDouble()
            ChartNormPoint(
                x = xValue,
                p01 = params.p01,
                p1 = params.p1,
                p3 = params.p3,
                p5 = params.p5,
                p10 = params.p10,
                p15 = params.p15,
                p25 = params.p25,
                p50 = params.p50,
                p75 = params.p75,
                p85 = params.p85,
                p90 = params.p90,
                p95 = params.p95,
                p97 = params.p97,
                p99 = params.p99,
                p999 = params.p999
            )
        }
        return ResponseEntity.ok(points)
    }
}
