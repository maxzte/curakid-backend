package com.misterio.curakid.admin.vaccines

import com.misterio.curakid.core.database.event.VaccineType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class VaccineCalendarParserTest {

    private val parser = VaccineCalendarParser()

    @Test
    fun `should correctly parse vaccine calendar from saved html fixture`() {
        val resource = javaClass.classLoader.getResource("vaccine-calendar.html")
            ?: File("src/test/resources/vaccine-calendar.html").toURI().toURL()
        val html = resource.readText()

        val parsed = parser.parse(html)

        assertTrue(parsed.isNotEmpty(), "Parsed vaccines should not be empty")

        // Check BCG
        val bcg = parsed.find { it.name == "БЦЖ" }
        assertNotNull(bcg, "BCG should be present")
        assertEquals(2, bcg?.scheduledAgeDays)
        assertEquals(0, bcg?.scheduledAgeMonths)
        assertEquals(1, bcg?.doseNumber)
        assertEquals(VaccineType.VACCINATION, bcg?.type)

        // Check HPV (ВПЛ)
        val hpv = parsed.find { it.name == "ВПЛ" }
        assertNotNull(hpv, "HPV should be parsed with name ВПЛ")
        assertEquals(144, hpv?.scheduledAgeMonths, "12 years = 144 months")
        assertTrue(hpv?.notes?.contains("дівчат") == true)

        // Check Hepatitis B
        val hepB = parsed.filter { it.name == "Гепатит В" }
        assertEquals(4, hepB.size, "Hepatitis B has 4 doses in calendar")
        assertEquals(listOf(2, 4, 6, 18), hepB.map { it.scheduledAgeMonths })

        // Check that adult row/columns are excluded
        val adultEntries = parsed.filter { it.scheduledAgeMonths != null && it.scheduledAgeMonths!! > 200 }
        assertTrue(adultEntries.isEmpty(), "Adults (10 years recurring) should be skipped")
    }
}
