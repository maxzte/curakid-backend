package com.misterio.curakid.admin

import com.misterio.curakid.admin.service.AdminFuzzyService
import com.misterio.curakid.core.database.knowledge.FuzzyDomain
import com.misterio.curakid.core.database.knowledge.FuzzyRuleEntity
import com.misterio.curakid.core.database.knowledge.FuzzyRuleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdminFuzzyServiceTest {

    @Mock lateinit var repository: FuzzyRuleRepository
    private lateinit var service: AdminFuzzyService

    @BeforeEach
    fun setUp() {
        service = AdminFuzzyService(repository)
    }

    @Test
    fun `getRules should call repository findAll`() {
        val rule = FuzzyRuleEntity(domain = FuzzyDomain.GENERAL).apply { id = UUID.randomUUID() }
        whenever(repository.findAll()).thenReturn(listOf(rule))

        val result = service.getRules()

        assertEquals(1, result.size)
        verify(repository).findAll()
    }

    @Test
    fun `updateRules should clear old rules and save new ones`() {
        val newRules = listOf(mapOf("domain" to "PHYSICAL", "conditions" to listOf(mapOf("variable" to "zscore", "term" to "low")), "consequent" to "HIGH", "isActive" to true))
        whenever(repository.saveAll(any<List<FuzzyRuleEntity>>())).thenAnswer {
            (it.arguments[0] as List<FuzzyRuleEntity>).onEach { r -> if (r.id == null) r.id = UUID.randomUUID() }
        }

        val result = service.updateRules(newRules)

        verify(repository).deleteAll()
        verify(repository).saveAll(any<List<FuzzyRuleEntity>>())
        assertEquals(1, result.size)
        assertEquals("PHYSICAL", result[0].domain)
    }

    @Test
    fun `toggleRule should flip isActive flag`() {
        val ruleId = UUID.randomUUID()
        val rule = FuzzyRuleEntity(domain = FuzzyDomain.GENERAL).apply { id = ruleId; isActive = true }
        whenever(repository.findById(ruleId)).thenReturn(Optional.of(rule))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] }

        val result = service.toggleRule(ruleId)

        assertFalse(result.isActive)
        verify(repository).save(rule)
    }
}
