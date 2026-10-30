package com.misterio.curakid.feature.children

import com.misterio.curakid.core.database.children.Child
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class ChildAgeTest {

    @ParameterizedTest(name = "age={0}m, premature={1}, weeks={2} -> expected={3}m")
    @CsvSource(
        // non-premature
        "6, false, 40, 6",
        // premature, corrected age applies
        "8, true, 32, 6",
        "10, true, 36, 9",
        // boundary: 40 weeks or more
        "6, true, 40, 6",
        "6, true, 42, 6",
        "6, true, 44, 6",
        // boundary: age >= 24 months (corrected age ceases to apply)
        "24, true, 32, 24",
        "25, true, 32, 25",
        // non-negative clamp
        "1, true, 28, 0"
    )
    fun `should calculate corrected age according to clinical boundary matrix`(
        chronologicalMonths: Long,
        isPremature: Boolean,
        gestationalWeeks: Int,
        expectedAgeMonths: Int
    ) {
        val birthDate = LocalDate.now().minusMonths(chronologicalMonths)
        val child = Child(
            birthDate = birthDate,
            isPremature = isPremature,
            gestationalWeeks = gestationalWeeks
        )

        assertEquals(expectedAgeMonths, child.calculateCorrectedAgeMonths())
    }

    @Test
    fun `should return chronological age when gestational weeks is null`() {
        val birthDate = LocalDate.now().minusMonths(8)
        val child = Child(birthDate = birthDate, isPremature = true, gestationalWeeks = null)

        assertEquals(8, child.calculateCorrectedAgeMonths())
    }
}
