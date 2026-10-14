package com.misterio.curakid.config

import com.google.common.cache.CacheBuilder
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

    companion object {
        private const val MAX_CACHE_SIZE = 2_000L
        private const val TTL_MINUTES = 30L
    }

    @Bean
    fun cacheManager(): CacheManager {
        return object : ConcurrentMapCacheManager("lifePeriods", "assessmentReadiness", "chatHistory") {
            override fun createConcurrentMapCache(name: String): Cache {
                val guavaCache = CacheBuilder.newBuilder()
                    .maximumSize(MAX_CACHE_SIZE)
                    .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
                    .build<Any, Any>()

                return ConcurrentMapCache(name, guavaCache.asMap(), true)
            }
        }
    }
}
