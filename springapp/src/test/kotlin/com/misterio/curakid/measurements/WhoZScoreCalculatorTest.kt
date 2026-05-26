package com.misterio.curakid.measurements

import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.medical.WhoZScoreCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class WhoZScoreCalculatorTest {

    private val calculator = WhoZScoreCalculator()

    @BeforeEach
    fun setup() {
        calculator.init()
    }

    @Test
    fun `WFA z-score for healthy 6-month-old boy should be near zero`() {
        val birthDate = LocalDate.now().minusMonths(6)
        // WHO median for 6-month male is ~7.9 kg
        val result = calculator.calculateWfaZscore(BigDecimal("7.9"), birthDate, Gender.MALE)
        assertTrue(
            result.abs() < BigDecimal("1.5"),
            "Z-score should be near 0 for median weight, got $result"
        )
    }

    @Test
    fun `WFA z-score for newborn should be calculated`() {
        val birthDate = LocalDate.now()
        // WHO median for newborn boy is ~3.3 kg
        val result = calculator.calculateWfaZscore(BigDecimal("3.3"), birthDate, Gender.MALE)
        assertTrue(
            result.abs() < BigDecimal("1.0"),
            "Newborn Z-score should be near 0, got $result"
        )
    }

    @Test
    fun `WFA z-score for 5-year-old (max age) should be calculated`() {
        val birthDate = LocalDate.now().minusDays(1856)
        // WHO median for 5-year male is ~18.3 kg
        val result = calculator.calculateWfaZscore(BigDecimal("18.3"), birthDate, Gender.MALE)
        assertTrue(
            result.abs() < BigDecimal("1.0"),
            "Max age Z-score should be near 0, got $result"
        )
    }

    @Test
    fun `Age above 1856 days should be coerced to 1856`() {
        val birthDate1856 = LocalDate.now().minusDays(1856)
        val birthDate2000 = LocalDate.now().minusDays(2000)

        val res1 = calculator.calculateWfaZscore(BigDecimal("18.3"), birthDate1856, Gender.MALE)
        val res2 = calculator.calculateWfaZscore(BigDecimal("18.3"), birthDate2000, Gender.MALE)

        assertEquals(res1, res2, "Should be coerced to same value as max age")
    }

    @Test
    fun `HFA z-score for healthy 12-month-old girl should be near zero`() {
        val birthDate = LocalDate.now().minusMonths(12)
        // WHO median for 12-month female is ~74.0 cm
        val result = calculator.calculateHfaZscore(BigDecimal("74.0"), birthDate, Gender.FEMALE)
        assertTrue(
            result.abs() < BigDecimal("1.5"),
            "Z-score should be near 0 for median height, got $result"
        )
    }

    @Test
    fun `BMI z-score for underweight child should be negative`() {
        val birthDate = LocalDate.now().minusMonths(12)
        val result = calculator.calculateBmiZscore(
            BigDecimal("6.0"),
            BigDecimal("74.0"),
            birthDate,
            Gender.MALE
        )
        assertTrue(
            result < BigDecimal.ZERO,
            "Underweight BMI z-score should be negative, got $result"
        )
    }

    @Test
    fun `Z-score should be capped at -5 and 5`() {
        val birthDate = LocalDate.now().minusMonths(12)
        val extremelyLow = calculator.calculateWfaZscore(BigDecimal("0.1"), birthDate, Gender.MALE)
        val extremelyHigh =
            calculator.calculateWfaZscore(BigDecimal("50.0"), birthDate, Gender.MALE)

        assertTrue(extremelyLow >= BigDecimal("-5"), "Should not go below -5")
        assertTrue(extremelyHigh <= BigDecimal("5"), "Should not exceed 5")
    }

    @Test
    fun `Missing gender should fallback to male`() {
        val birthDate = LocalDate.now().minusMonths(6)
        val resMale = calculator.calculateWfaZscore(BigDecimal("7.9"), birthDate, Gender.MALE)
        val resNull = calculator.calculateWfaZscore(BigDecimal("7.9"), birthDate, null)

        assertEquals(resMale, resNull, "Null gender should fallback to Male")
    }
}
