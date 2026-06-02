package com.misterio.curakid.feature.auth.service

import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.core.database.auth.LegalDocument
import com.misterio.curakid.core.database.auth.LegalDocumentRepository
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyActivatedException
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyExistException
import com.misterio.curakid.feature.auth.LegalDocumentNotFoundException
import com.misterio.curakid.feature.auth.LegalDocumentResponse
import com.misterio.curakid.feature.auth.model.CreateLegalDocumentRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class LegalDocumentService(
    private val repository: LegalDocumentRepository,
) {
    private val logger = LoggerFactory.getLogger(LegalDocumentService::class.java)

    fun getActiveDocuments(): List<LegalDocumentResponse> {
        return try {
            repository.findAllByIsActiveTrue().map { it.toResponse() }
        } catch (e: Exception) {
            logger.debug("Failed to load legal documents: ${e.message}", e)
            throw e
        }
    }

    fun getActiveDocument(type: ConsentType): LegalDocumentResponse {
        return repository.findByTypeAndIsActiveTrue(type)
            ?.toResponse()
            ?: throw LegalDocumentNotFoundException("Документ $type не знайдено")
    }

    @Transactional
    fun uploadDocument(request: CreateLegalDocumentRequest): LegalDocumentResponse {
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

        return document.toResponse()
    }

    // Активувати версію документу
    // Деактивує попередню активну версію
    @Transactional
    fun activateDocument(documentId: UUID): LegalDocumentResponse {
        val document = repository.findById(documentId)
            .orElseThrow { LegalDocumentNotFoundException("Документ не знайдено") }

        if (document.isActive) {
            throw LegalDocumentAlreadyActivatedException("Документ вже активний")
        }

        // Деактивуємо попередню версію
        repository.deactivateAllByType(document.type)

        document.isActive = true
        val activated = repository.save(document)

        return activated.toResponse()
    }
}

private fun LegalDocument.toResponse() = LegalDocumentResponse(
    type = type.name,
    version = version,
    url = url,
    publishedAt = createdAt!!,
)
