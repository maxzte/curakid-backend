package com.misterio.curakid.admin

import com.misterio.curakid.admin.service.AdminLegalService
import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.core.database.auth.LegalDocument
import com.misterio.curakid.core.database.auth.LegalDocumentRepository
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyActivatedException
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyExistException
import com.misterio.curakid.feature.auth.LegalDocumentNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
class AdminLegalServiceTest {

    @Mock lateinit var repository: LegalDocumentRepository
    private lateinit var service: AdminLegalService

    @BeforeEach
    fun setUp() {
        service = AdminLegalService(repository)
    }

    @Test
    fun `getAllDocuments should return all documents`() {
        val docs = listOf(
            LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/terms/v1", isActive = true).apply { createdAt = Instant.now() },
            LegalDocument(type = ConsentType.PRIVACY_POLICY, version = "1.0", url = "/privacy/v1", isActive = false).apply { createdAt = Instant.now() }
        )
        whenever(repository.findAll()).thenReturn(docs)

        val result = service.getAllDocuments()

        assertEquals(2, result.size)
    }

    @Test
    fun `uploadDocument should save and return new document`() {
        val request = CreateLegalDocumentRequest(type = "TERMS", version = "1.0", url = "/terms/v1")
        whenever(repository.findByTypeAndVersion(ConsentType.TERMS, "1.0")).thenReturn(null)
        whenever(repository.findByUrl("/terms/v1")).thenReturn(null)
        whenever(repository.save(any<LegalDocument>())).thenAnswer {
            (it.arguments[0] as LegalDocument).also { doc -> doc.createdAt = Instant.now() }
        }

        val result = service.uploadDocument(request)

        assertEquals("TERMS", result.type)
        assertEquals("1.0", result.version)
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
        assertTrue(result.isActive)
        verify(repository).deactivateAllByType(ConsentType.TERMS)
    }

    @Test
    fun `activateDocument should throw when already active`() {
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
    fun `activateDocument should throw when not found`() {
        val docId = UUID.randomUUID()
        whenever(repository.findById(docId)).thenReturn(Optional.empty())

        assertThrows<LegalDocumentNotFoundException> {
            service.activateDocument(docId)
        }
    }

    @Test
    fun `deactivateDocument should set isActive to false`() {
        val docId = UUID.randomUUID()
        val doc = LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/terms/v1", isActive = true).apply {
            createdAt = Instant.now()
            id = docId
        }
        whenever(repository.findById(docId)).thenReturn(Optional.of(doc))
        whenever(repository.save(any<LegalDocument>())).thenAnswer { it.arguments[0] }

        val result = service.deactivateDocument(docId)

        assertFalse(result.isActive)
        assertFalse(doc.isActive)
    }

    @Test
    fun `deactivateDocument should throw when already inactive`() {
        val docId = UUID.randomUUID()
        val doc = LegalDocument(type = ConsentType.TERMS, version = "1.0", url = "/terms/v1", isActive = false).apply {
            createdAt = Instant.now()
            id = docId
        }
        whenever(repository.findById(docId)).thenReturn(Optional.of(doc))

        assertThrows<LegalDocumentAlreadyActivatedException> {
            service.deactivateDocument(docId)
        }
    }

    @Test
    fun `deactivateDocument should throw when not found`() {
        val docId = UUID.randomUUID()
        whenever(repository.findById(docId)).thenReturn(Optional.empty())

        assertThrows<LegalDocumentNotFoundException> {
            service.deactivateDocument(docId)
        }
    }
}
