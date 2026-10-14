package com.misterio.curakid.core.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.support.TaskExecutorAdapter
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    @Bean
    @Primary
    fun applicationTaskExecutor(): TaskExecutor {
        return TaskExecutorAdapter(
            DelegatingSecurityContextExecutorService(
                Executors.newVirtualThreadPerTaskExecutor()
            )
        )
    }

    override fun getAsyncExecutor(): Executor {
        return DelegatingSecurityContextExecutorService(
            Executors.newVirtualThreadPerTaskExecutor()
        )
    }
}
