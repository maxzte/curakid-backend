package com.misterio.curakid.core.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    @Bean
    @Primary
    fun applicationTaskExecutor(): TaskExecutor {
        val executor = SimpleAsyncTaskExecutor("curakid-vt-")
        executor.setVirtualThreads(true)
        executor.concurrencyLimit = 250 // Bulkhead to prevent memory/resource starvation under 1M DAU peak
        executor.setTaskTerminationTimeout(25_000L)
        executor.setTaskDecorator { runnable -> DelegatingSecurityContextRunnable(runnable) }
        return executor
    }

    override fun getAsyncExecutor(): Executor {
        return applicationTaskExecutor()
    }
}
