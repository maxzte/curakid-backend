package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.medical.ChartType
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MeasurementReferenceControllerTest {

    private lateinit var mockMvc: MockMvc
    private lateinit var calculator: WhoPercentileCalculator

    @BeforeEach
    fun setUp() {
        calculator = mock()
        val controller = MeasurementReferenceController(calculator)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `GET norms returns chart points`() {
        val params = WhoPercentileCalculator.PercentileParams(
            p01 = 0.1, p1 = 1.0, p3 = 3.0, p5 = 5.0,
            p10 = 10.0, p15 = 15.0, p25 = 25.0, p50 = 50.0,
            p75 = 75.0, p85 = 85.0, p90 = 90.0, p95 = 95.0,
            p97 = 97.0, p99 = 99.0, p999 = 99.9
        )
        whenever(calculator.getRawMap(ChartType.WFA, Gender.MALE)).thenReturn(
            sortedMapOf(30 to params)
        )

        mockMvc.perform(get("/api/measurements/norms/WFA/MALE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].x").value(30.0))
            .andExpect(jsonPath("$[0].p50").value(50.0))
    }

    private fun sortedMapOf(vararg pairs: Pair<Int, WhoPercentileCalculator.PercentileParams>): java.util.SortedMap<Int, WhoPercentileCalculator.PercentileParams> {
        return java.util.TreeMap(mapOf(*pairs))
    }
}
