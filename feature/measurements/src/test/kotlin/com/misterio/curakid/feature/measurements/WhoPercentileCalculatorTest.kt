package com.misterio.curakid.feature.measurements

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.medical.ChartType
import com.misterio.curakid.core.medical.WhoPercentileCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WhoPercentileCalculatorTest {

    private val calculator = WhoPercentileCalculator()

    @BeforeEach
    fun setUp() {
        calculator.init()
    }

    @ParameterizedTest(name = "chartType={0}, gender={1}, ageDays={2} -> monotonic percentiles P3 < P50 < P97")
    @CsvSource(
        "WFA, MALE, 0",
        "WFA, FEMALE, 180",
        "HFA, MALE, 365",
        "HFA, FEMALE, 730",
        "BMI, MALE, 1000",
        "HCFA, FEMALE, 90",
        "WFA, MALE, 2500"
    )
    fun `getParamsForAge should return monotonically increasing percentiles across chart types and age boundaries`(
        chartType: ChartType,
        gender: Gender,
        ageDays: Int
    ) {
        // When
        val params = calculator.getParamsForAge(chartType, gender, ageDays)

        // Then
        assertNotNull(params)
        val p = requireNotNull(params)
        assertTrue(p.p3 < p.p15, "P3 (${p.p3}) should be < P15 (${p.p15})")
        assertTrue(p.p15 < p.p50, "P15 (${p.p15}) should be < P50 (${p.p50})")
        assertTrue(p.p50 < p.p85, "P50 (${p.p50}) should be < P85 (${p.p85})")
        assertTrue(p.p85 < p.p97, "P85 (${p.p85}) should be < P97 (${p.p97})")
    }

    @ParameterizedTest(name = "ageDays={0} (<730 WFL vs >=730 WFH), heightCm={1}, gender={2}")
    @CsvSource(
        "365, 75.0, MALE",
        "729, 85.0, FEMALE",
        "730, 85.0, FEMALE",
        "1095, 95.0, MALE"
    )
    fun `getParamsForHeight should switch between WFL and WFH at 730 days boundary and return valid norms`(
        ageDays: Int,
        heightCm: Double,
        gender: Gender
    ) {
        // When
        val params = calculator.getParamsForHeight(gender, heightCm, ageDays)

        // Then
        assertNotNull(params)
        val p = requireNotNull(params)
        assertTrue(p.p3 < p.p50 && p.p50 < p.p97)
    }

    @ParameterizedTest(name = "keyPrefix={0}, expectedWfaKey={1}")
    @CsvSource(
        "WHO, WFA (kg)",
        "ZSCORE, wfaZscore"
    )
    fun `buildNormsMap should populate expected keys according to keyPrefix`(
        keyPrefix: String,
        expectedWfaKey: String
    ) {
        // When
        val norms = calculator.buildNormsMap(
            gender = Gender.MALE,
            ageDays = 180,
            heightCm = 67.0,
            keyPrefix = keyPrefix
        )

        // Then
        assertTrue(norms.containsKey(expectedWfaKey))
        val wfaNorms = requireNotNull(norms[expectedWfaKey])
        assertEquals(setOf("P3", "P15", "P50", "P85", "P97"), wfaNorms.keys)
    }
}
