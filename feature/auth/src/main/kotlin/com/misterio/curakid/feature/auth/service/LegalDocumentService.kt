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
import org.springframework.transaction.PlatformTransactionManager
import com.misterio.curakid.core.database.readTx
import com.misterio.curakid.core.database.writeTx
import java.util.*

@Service
class LegalDocumentService(
    private val repository: LegalDocumentRepository,
    private val transactionManager: PlatformTransactionManager
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

    fun uploadDocument(request: CreateLegalDocumentRequest): LegalDocumentResponse {
        val type = ConsentType.valueOf(request.type)

        transactionManager.readTx {
            if (repository.findByTypeAndVersion(type, request.version) != null) {
                throw LegalDocumentAlreadyExistException(
                    "Документ ${request.type} версії ${request.version} вже існує"
                )
            }

            if (repository.findByUrl(request.url) != null) {
                throw LegalDocumentAlreadyExistException("Файл за URL ${request.url} вже існує")
            }
        }

        val document = requireNotNull(transactionManager.writeTx {
            repository.save(
                LegalDocument(
                    type = type,
                    version = request.version,
                    url = request.url,
                    isActive = false,
                )
            )
        })

        return document.toResponse()
    }

    fun activateDocument(documentId: UUID): LegalDocumentResponse {
        val document = requireNotNull(transactionManager.readTx {
            repository.findById(documentId)
                .orElseThrow { LegalDocumentNotFoundException("Документ не знайдено") }
        })

        if (document.isActive) {
            throw LegalDocumentAlreadyActivatedException("Документ вже активний")
        }

        val activated = requireNotNull(transactionManager.writeTx {
            // Деактивуємо попередню версію
            repository.deactivateAllByType(document.type)
            document.isActive = true
            repository.save(document)
        })

        return activated.toResponse()
    }
}

private fun LegalDocument.toResponse() = LegalDocumentResponse(
    type = type.name,
    version = version,
    url = url,
    publishedAt = createdAt ?: java.time.Instant.now(),
)
