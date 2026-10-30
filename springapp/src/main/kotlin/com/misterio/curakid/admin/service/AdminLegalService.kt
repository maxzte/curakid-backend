package com.misterio.curakid.admin.service

import com.misterio.curakid.admin.AdminLegalDocumentResponse
import com.misterio.curakid.admin.CreateLegalDocumentRequest
import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.core.database.auth.LegalDocument
import com.misterio.curakid.core.database.auth.LegalDocumentRepository
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyActivatedException
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyExistException
import com.misterio.curakid.feature.auth.LegalDocumentNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdminLegalService(
    private val repository: LegalDocumentRepository,
) {
    fun getAllDocuments(): List<AdminLegalDocumentResponse> =
        repository.findAll().map { it.toAdminResponse() }

    fun uploadDocument(request: CreateLegalDocumentRequest): AdminLegalDocumentResponse {
        val type = ConsentType.valueOf(request.type)
        if (repository.findByTypeAndVersion(type, request.version) != null) {
            throw LegalDocumentAlreadyExistException(
                "Документ ${request.type} версії ${request.version} вже існує"
            )
        }
        if (repository.findByUrl(request.url) != null) {
            throw LegalDocumentAlreadyExistException("Файл за URL ${request.url} вже існує")
        }

        val document = repository.save(
            LegalDocument(
                type = type,
                version = request.version,
                url = request.url,
                isActive = false,
            )
        )
        return document.toAdminResponse()
    }

    @Transactional
    fun activateDocument(documentId: UUID): AdminLegalDocumentResponse {
        val document = repository.findById(documentId)
            .orElseThrow { LegalDocumentNotFoundException("Документ не знайдено") }

        if (document.isActive) {
            throw LegalDocumentAlreadyActivatedException("Документ вже активний")
        }

        repository.deactivateAllByType(document.type)
        document.isActive = true
        return repository.save(document).toAdminResponse()
    }

    fun deactivateDocument(documentId: UUID): AdminLegalDocumentResponse {
        val document = repository.findById(documentId)
            .orElseThrow { LegalDocumentNotFoundException("Документ не знайдено") }

        if (!document.isActive) {
            throw LegalDocumentAlreadyActivatedException("Документ вже неактивний")
        }

        document.isActive = false
        return repository.save(document).toAdminResponse()
    }
}

private fun LegalDocument.toAdminResponse() = AdminLegalDocumentResponse(
    id = requireNotNull(id) { "LegalDocument ID must not be null" },
    type = type.name,
    version = version,
    url = url,
    isActive = isActive,
    publishedAt = createdAt ?: java.time.Instant.now(),
)
