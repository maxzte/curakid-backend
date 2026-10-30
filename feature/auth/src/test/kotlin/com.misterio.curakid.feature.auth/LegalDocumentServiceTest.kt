package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.core.database.auth.LegalDocument
import com.misterio.curakid.core.database.auth.LegalDocumentRepository
import com.misterio.curakid.feature.auth.model.CreateLegalDocumentRequest
import com.misterio.curakid.feature.auth.service.LegalDocumentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LegalDocumentServiceTest {

    @Mock lateinit var repository: LegalDocumentRepository
    @Mock lateinit var transactionManager: PlatformTransactionManager
    private lateinit var service: LegalDocumentService

    @BeforeEach
    fun setUp() {
        service = LegalDocumentService(repository, transactionManager)
    }

    @Test
    fun `getActiveDocuments should return all active documents`() {
        val doc1 = LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/terms/v1", isActive = true).apply { createdAt = Instant.now() }
        val doc2 = LegalDocument(type = ConsentType.PRIVACY_POLICY, version = "2.0", url = "/privacy/v2", isActive = true).apply { createdAt = Instant.now() }
        whenever(repository.findAllByIsActiveTrue()).thenReturn(listOf(doc1, doc2))

        val result = service.getActiveDocuments()

        assertEquals(2, result.size)
        assertEquals("TERMS", result[0].type)
        assertEquals("PRIVACY_POLICY", result[1].type)
    }

    @Test
    fun `getActiveDocuments should return empty list when none active`() {
        whenever(repository.findAllByIsActiveTrue()).thenReturn(emptyList())

        val result = service.getActiveDocuments()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getActiveDocument should return document by type`() {
        val doc = LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/terms/v1", isActive = true).apply { createdAt = Instant.now() }
        whenever(repository.findByTypeAndIsActiveTrue(ConsentType.TERMS)).thenReturn(doc)

        val result = service.getActiveDocument(ConsentType.TERMS)

        assertEquals("TERMS", result.type)
        assertEquals("1.0", result.version)
    }

    @Test
    fun `uploadDocument should throw if version already exists`() {
        whenever(repository.findByTypeAndVersion(ConsentType.TERMS, "1.0")).thenReturn(
            LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/terms/v1", isActive = false)
        )

        val req = CreateLegalDocumentRequest(type = "TERMS", version = "1.0", url = "/terms/new")

        assertThrows<LegalDocumentAlreadyExistException> {
            service.uploadDocument(req)
        }
    }

    @Test
    fun `uploadDocument should throw if URL already exists`() {
        whenever(repository.findByTypeAndVersion(ConsentType.TERMS, "1.0")).thenReturn(null)
        whenever(repository.findByUrl("/terms/v1")).thenReturn(
            LegalDocument(type = ConsentType.TERMS, version = "0.9", url = "/terms/v1", isActive = false)
        )

        val req = CreateLegalDocumentRequest(type = "TERMS", version = "1.0", url = "/terms/v1")

        assertThrows<LegalDocumentAlreadyExistException> {
            service.uploadDocument(req)
        }
    }

    @Test
    fun `uploadDocument should create inactive document`() {
        whenever(repository.findByTypeAndVersion(ConsentType.TERMS, "1.0")).thenReturn(null)
        whenever(repository.findByUrl("/terms/v1")).thenReturn(null)
        whenever(repository.save(any())).thenAnswer { invocation ->
            (invocation.getArgument(0) as LegalDocument).apply { createdAt = Instant.now() }
        }

        val req = CreateLegalDocumentRequest(type = "TERMS", version = "1.0", url = "/terms/v1")
        val result = service.uploadDocument(req)

        assertEquals("TERMS", result.type)
        assertEquals("1.0", result.version)
        verify(repository).save(any())
    }

    @Test
    fun `activateDocument should throw if document not found`() {
        val id = UUID.randomUUID()
        whenever(repository.findById(id)).thenReturn(Optional.empty())

        assertThrows<LegalDocumentNotFoundException> {
            service.activateDocument(id)
        }
    }

    @Test
    fun `activateDocument should throw if already active`() {
        val id = UUID.randomUUID()
        val doc = LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/v1", isActive = true)
        whenever(repository.findById(id)).thenReturn(Optional.of(doc))

        assertThrows<LegalDocumentAlreadyActivatedException> {
            service.activateDocument(id)
        }
    }

    @Test
    fun `activateDocument should deactivate previous and activate selected`() {
        val id = UUID.randomUUID()
        val doc = LegalDocument(type = ConsentType.TERMS, version = "2.0", url = "/v2", isActive = false).apply { createdAt = Instant.now() }
        whenever(repository.findById(id)).thenReturn(Optional.of(doc))
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }

        val result = service.activateDocument(id)

        verify(repository).deactivateAllByType(ConsentType.TERMS)
        verify(repository).save(doc)
        assertTrue(doc.isActive)
        assertEquals("2.0", result.version)
    }
}
