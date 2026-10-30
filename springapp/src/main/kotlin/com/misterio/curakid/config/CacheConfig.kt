package com.misterio.curakid.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    @ConditionalOnBean(RedisConnectionFactory::class)
    fun redisCacheManager(
        connectionFactory: RedisConnectionFactory,
        objectMapper: ObjectMapper
    ): RedisCacheManager {
        val jsonSerializer = RedisSerializer.json()
        val stringSerializer = StringRedisSerializer()

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
            .entryTtl(Duration.ofMinutes(30))
            .disableCachingNullValues()

        val cacheConfigs = mapOf(
            "lifePeriods" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "assessmentReadiness" to defaultConfig.entryTtl(Duration.ofMinutes(60)),
            "chatHistory" to defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "fuzzyRules" to defaultConfig.entryTtl(Duration.ofHours(6)),
            "queryEmbeddings" to defaultConfig.entryTtl(Duration.ofHours(24)),
            "vaccineTemplates" to defaultConfig.entryTtl(Duration.ofHours(12))
        )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build()
    }

    @Bean
    @ConditionalOnMissingBean(CacheManager::class)
    fun fallbackCacheManager(): CacheManager {
        return ConcurrentMapCacheManager(
            "lifePeriods", "assessmentReadiness", "chatHistory",
            "fuzzyRules", "queryEmbeddings", "vaccineTemplates"
        )
    }
}
