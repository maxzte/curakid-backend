package com.misterio.curakid.core.database.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OAuthProviderRepository : JpaRepository<OAuthProvider, UUID> {
    fun findByProviderNameAndProviderUserId(providerName: String, providerUserId: String): Optional<OAuthProvider>
}
