package com.misterio.curakid.core.database.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.*

interface LegalDocumentRepository : JpaRepository<LegalDocument, UUID> {

    fun findByUrl(url: String): LegalDocument?

    fun findByTypeAndVersion(type: ConsentType, version: String): LegalDocument?

    fun findByTypeAndIsActiveTrue(type: ConsentType): LegalDocument?

    fun findAllByIsActiveTrue(): List<LegalDocument>

    @Modifying
    @Query("UPDATE LegalDocument l SET l.isActive = false WHERE l.type = :type")
    fun deactivateAllByType(type: ConsentType)

}
