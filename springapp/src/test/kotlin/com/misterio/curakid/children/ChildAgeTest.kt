package com.misterio.curakid.children

import com.misterio.curakid.core.database.children.Child
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChildAgeTest {

    @Test
    fun `should return chronological age for non-premature baby`() {
        val birthDate = LocalDate.now().minusMonths(6)
        val child = Child(birthDate = birthDate, isPremature = false)

        assertEquals(6, child.calculateCorrectedAgeMonths())
    }

    @Test
    fun `should calculate corrected age for premature baby`() {
        // 8 months old, born at 32 weeks (8 weeks early = 2 months early)
        // Corrected age = 8 - 2 = 6 months
        val birthDate = LocalDate.now().minusMonths(8)
        val child = Child(birthDate = birthDate, isPremature = true, gestationalWeeks = 32)

        assertEquals(6, child.calculateCorrectedAgeMonths())
    }

    @Test
    fun `should return chronological age if gestational weeks is 40`() {
        val birthDate = LocalDate.now().minusMonths(6)
        val child = Child(birthDate = birthDate, isPremature = true, gestationalWeeks = 40)

        assertEquals(6, child.calculateCorrectedAgeMonths())
    }

    @Test
    fun `should return chronological age if gestational weeks is 42`() {
        val birthDate = LocalDate.now().minusMonths(6)
        val child = Child(birthDate = birthDate, isPremature = true, gestationalWeeks = 42)

        assertEquals(6, child.calculateCorrectedAgeMonths())
    }

    @Test
    fun `should return chronological age if gestational weeks is 44`() {
        val birthDate = LocalDate.now().minusMonths(6)
        val child = Child(birthDate = birthDate, isPremature = true, gestationalWeeks = 44)

        assertEquals(6, child.calculateCorrectedAgeMonths())
    }

    @Test
    fun `should return chronological age if child is 2 years or older`() {
        val birthDate = LocalDate.now().minusMonths(25)
        val child = Child(birthDate = birthDate, isPremature = true, gestationalWeeks = 32)

        assertEquals(25, child.calculateCorrectedAgeMonths())
    }

    @Test
    fun `should not return negative age`() {
        // 1 month old, born 12 weeks early (3 months early)
        val birthDate = LocalDate.now().minusMonths(1)
        val child = Child(birthDate = birthDate, isPremature = true, gestationalWeeks = 28)

        assertEquals(0, child.calculateCorrectedAgeMonths())
    }
}
