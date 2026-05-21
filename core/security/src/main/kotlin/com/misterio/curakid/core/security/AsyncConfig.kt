package com.misterio.curakid.core.security

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
class AsyncConfig : AsyncConfigurer {
    override fun getAsyncExecutor(): Executor {
        return DelegatingSecurityContextExecutorService(
            Executors.newCachedThreadPool()
        )
    }
}
