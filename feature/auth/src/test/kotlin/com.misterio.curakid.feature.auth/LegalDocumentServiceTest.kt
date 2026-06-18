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
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class LegalDocumentServiceTest {

    @Mock lateinit var repository: LegalDocumentRepository
    private lateinit var service: LegalDocumentService

    @BeforeEach
    fun setUp() {
        service = LegalDocumentService(repository)
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
    fun `getActiveDocument should throw when type not found`() {
        whenever(repository.findByTypeAndIsActiveTrue(ConsentType.TERMS)).thenReturn(null)

        assertThrows<LegalDocumentNotFoundException> {
            service.getActiveDocument(ConsentType.TERMS)
        }
    }

    @Test
    fun `uploadDocument should save and return new document`() {
        val request = CreateLegalDocumentRequest(type = "TERMS", version = "1.0", url = "/terms/v1")
        whenever(repository.findByTypeAndVersion(ConsentType.TERMS, "1.0")).thenReturn(null)
        whenever(repository.findByUrl("/terms/v1")).thenReturn(null)
        whenever(repository.save(any<LegalDocument>())).thenAnswer { (it.arguments[0] as LegalDocument).also { doc -> doc.createdAt = Instant.now() } }

        val result = service.uploadDocument(request)

        assertEquals("TERMS", result.type)
        assertEquals("1.0", result.version)
        assertEquals("/terms/v1", result.url)
        verify(repository).save(any<LegalDocument>())
    }

    @Test
    fun `uploadDocument should throw when version already exists`() {
        val request = CreateLegalDocumentRequest(type = "TERMS", version = "1.0", url = "/terms/v1")
        whenever(repository.findByTypeAndVersion(ConsentType.TERMS, "1.0")).thenReturn(LegalDocument())

        assertThrows<LegalDocumentAlreadyExistException> {
            service.uploadDocument(request)
        }
    }

    @Test
    fun `uploadDocument should throw when URL already exists`() {
        val request = CreateLegalDocumentRequest(type = "TERMS", version = "2.0", url = "/terms/v2")
        whenever(repository.findByTypeAndVersion(ConsentType.TERMS, "2.0")).thenReturn(null)
        whenever(repository.findByUrl("/terms/v2")).thenReturn(LegalDocument())

        assertThrows<LegalDocumentAlreadyExistException> {
            service.uploadDocument(request)
        }
    }

    @Test
    fun `activateDocument should activate and deactivate previous`() {
        val docId = UUID.randomUUID()
        val doc = LegalDocument(type = ConsentType.TERMS, version = "2.0", url = "/terms/v2", isActive = false).apply {
            createdAt = Instant.now()
            id = docId
        }
        whenever(repository.findById(docId)).thenReturn(Optional.of(doc))
        whenever(repository.save(any<LegalDocument>())).thenAnswer { it.arguments[0] }

        val result = service.activateDocument(docId)

        assertEquals("2.0", result.version)
        verify(repository).deactivateAllByType(ConsentType.TERMS)
    }

    @Test
    fun `activateDocument should throw when document already active`() {
        val docId = UUID.randomUUID()
        val doc = LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/terms/v1", isActive = true).apply {
            createdAt = Instant.now()
            id = docId
        }
        whenever(repository.findById(docId)).thenReturn(Optional.of(doc))

        assertThrows<LegalDocumentAlreadyActivatedException> {
            service.activateDocument(docId)
        }
    }

    @Test
    fun `activateDocument should throw when document not found`() {
        val docId = UUID.randomUUID()
        whenever(repository.findById(docId)).thenReturn(Optional.empty())

        assertThrows<LegalDocumentNotFoundException> {
            service.activateDocument(docId)
        }
    }
}
