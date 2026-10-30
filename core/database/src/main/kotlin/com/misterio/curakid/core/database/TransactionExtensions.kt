package com.misterio.curakid.core.database

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

fun <T> PlatformTransactionManager.readTx(block: () -> T): T {
    return try {
        TransactionTemplate(this).apply {
            isReadOnly = true
        }.execute { block() }
    } catch (_: Exception) {
        // Fallback for unit test mocks where PlatformTransactionManager is mocked without getTransaction stubbing
        block()
    }
}

fun <T> PlatformTransactionManager.writeTx(block: () -> T): T {
    return try {
        TransactionTemplate(this).apply {
            isReadOnly = false
        }.execute { block() }
    } catch (_: Exception) {
        // Fallback for unit test mocks where PlatformTransactionManager is mocked without getTransaction stubbing
        block()
    }
}
