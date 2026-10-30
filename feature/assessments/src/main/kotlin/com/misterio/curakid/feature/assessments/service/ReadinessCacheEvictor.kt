package com.misterio.curakid.feature.assessments.service

import com.misterio.curakid.core.database.development.LifePeriodRepository
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Component
class ReadinessCacheEvictor(
    private val lifePeriodRepository: LifePeriodRepository,
    private val cacheManager: CacheManager? = null
) {

    fun evict(childId: UUID?, periodCode: String? = null) {
        if (childId == null) return
        val cache = cacheManager?.getCache(CACHE_NAME) ?: return

        val codes = try {
            lifePeriodRepository.findAllCodes()
        } catch (e: Exception) {
            emptyList()
        }

        val action = {
            if (periodCode != null) {
                cache.evict("${childId}_$periodCode")
            }
            codes.forEach { code ->
                cache.evict("${childId}_$code")
            }
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    action()
                }
            })
        } else {
            action()
        }
    }

    private companion object {
        private const val CACHE_NAME = "assessmentReadiness"
    }
}
