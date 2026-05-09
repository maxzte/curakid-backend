package com.misterio.curakid.admin

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class AdminFuzzyServiceTest {

    private lateinit var repository: FuzzyRuleConfigRepository
    private lateinit var service: AdminFuzzyService

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = AdminFuzzyService(repository)
    }

    @Test
    fun `getRules should return existing or create default`() {
        whenever(repository.findByConfigKey("default")).thenReturn(Optional.empty())
        whenever(repository.save(any<FuzzyRuleConfig>())).thenAnswer { it.arguments[0] }

        val result = service.getRules("default")

        assertNotNull(result)
        assertEquals("default", result.configKey)
        verify(repository).save(any())
    }

    @Test
    fun `updateRules should save updated list`() {
        val config = FuzzyRuleConfig(configKey = "default")
        whenever(repository.findByConfigKey("default")).thenReturn(Optional.of(config))
        whenever(repository.save(any<FuzzyRuleConfig>())).thenAnswer { it.arguments[0] }

        val newRules = listOf(mapOf("rule" to "if weight is low then urgency is high"))
        val result = service.updateRules("default", newRules)

        assertEquals(newRules, result.rules)
        verify(repository).save(config)
    }
}
