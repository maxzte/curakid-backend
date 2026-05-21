package com.misterio.curakid.admin

import com.misterio.curakid.core.database.knowledge.FuzzyDomain
import com.misterio.curakid.core.database.knowledge.FuzzyRuleEntity
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class AdminFuzzyServiceTest {

    private lateinit var repository: FuzzyRuleRepository
    private lateinit var service: AdminFuzzyService

    @BeforeEach
    fun setUp() {
        repository = mock()
        service = AdminFuzzyService(repository)
    }

    @Test
    fun `getRules should call repository findAll`() {
        whenever(repository.findAll()).thenReturn(listOf(FuzzyRuleEntity(domain = FuzzyDomain.GENERAL)))
        
        val result = service.getRules()
        
        assertEquals(1, result.size)
        verify(repository).findAll()
    }

    @Test
    fun `updateRules should clear old rules and save new ones`() {
        val newRules = listOf(
            mapOf(
                "domain" to "PHYSICAL",
                "conditions" to listOf(mapOf("variable" to "zscore", "term" to "low")),
                "consequent" to "HIGH",
                "isActive" to true
            )
        )
        whenever(repository.saveAll(any<List<FuzzyRuleEntity>>())).thenAnswer { it.arguments[0] }

        val result = service.updateRules(newRules)

        verify(repository).deleteAll()
        verify(repository).saveAll(any<List<FuzzyRuleEntity>>())
        assertEquals(1, result.size)
        assertEquals(FuzzyDomain.PHYSICAL, result[0].domain)
    }

    @Test
    fun `toggleRule should flip isActive flag`() {
        val ruleId = UUID.randomUUID()
        val rule = FuzzyRuleEntity(domain = FuzzyDomain.GENERAL).apply { 
            id = ruleId
            isActive = true 
        }
        whenever(repository.findById(ruleId)).thenReturn(Optional.of(rule))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val result = service.toggleRule(ruleId)

        assertFalse(result.isActive)
        verify(repository).save(rule)
    }
}
