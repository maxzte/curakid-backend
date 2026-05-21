package com.misterio.curakid.feature.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface OAuthProviderRepository : JpaRepository<OAuthProvider, UUID> {
    fun findByProviderNameAndProviderUserId(providerName: String, providerUserId: String): Optional<OAuthProvider>
}
