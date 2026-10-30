package com.misterio.curakid.admin

import com.misterio.curakid.admin.service.AdminKnowledgeService
import com.misterio.curakid.core.database.knowledge.MedicalCategory
import com.misterio.curakid.core.database.knowledge.MedicalKnowledge
import com.misterio.curakid.core.database.knowledge.MedicalKnowledgeRepository
import com.misterio.curakid.core.essential.common.analysis.EmbeddingProvider
import com.misterio.curakid.feature.knowledge.MedicalCategoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdminKnowledgeServiceTest {

    @Mock lateinit var knowledgeRepository: MedicalKnowledgeRepository
    @Mock lateinit var categoryRepository: MedicalCategoryRepository
    @Mock lateinit var embeddingProvider: EmbeddingProvider
    private lateinit var service: AdminKnowledgeService

    @BeforeEach
    fun setUp() {
        service = AdminKnowledgeService(knowledgeRepository, categoryRepository, embeddingProvider)
    }

    @Test
    fun `uploadArticle should chunk text and save articles`() {
        val categoryId = UUID.randomUUID()
        val category = MedicalCategory(name = "nutrition", displayName = "Харчування").apply { id = categoryId }
        whenever(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category))
        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(listOf(0.1f, 0.2f, 0.3f))
        whenever(knowledgeRepository.saveAll(any<Iterable<MedicalKnowledge>>())).thenAnswer { (it.arguments[0] as Iterable<MedicalKnowledge>).toList() }

        val results = service.uploadArticle("Healthy Diet", "Eat vegetables and fruits daily", categoryId)

        assertEquals(1, results.size)
        assertEquals("Healthy Diet", results[0].title)
        assertEquals("Eat vegetables and fruits daily", results[0].content)
        verify(embeddingProvider).generateEmbedding(any())
        verify(knowledgeRepository).saveAll(any<Iterable<MedicalKnowledge>>())
    }

    @Test
    fun `uploadArticle should chunk long text into multiple chunks`() {
        val categoryId = UUID.randomUUID()
        val category = MedicalCategory(name = "nutrition", displayName = "Харчування").apply { id = categoryId }
        val longText = "A ".repeat(3000)

        whenever(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category))
        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(listOf(0.1f, 0.2f, 0.3f))
        whenever(knowledgeRepository.saveAll(any<Iterable<MedicalKnowledge>>())).thenAnswer { (it.arguments[0] as Iterable<MedicalKnowledge>).toList() }

        val results = service.uploadArticle("Long Article", longText, categoryId)

        assertTrue(results.size > 1)
        results.forEach { assertEquals("Long Article", it.title) }
    }

    @Test
    fun `uploadArticle should throw when category not found`() {
        val categoryId = UUID.randomUUID()
        whenever(categoryRepository.findById(categoryId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            service.uploadArticle("Title", "Content", categoryId)
        }
    }

    @Test
    fun `uploadArticle should throw when embedding fails`() {
        val categoryId = UUID.randomUUID()
        val category = MedicalCategory(name = "nutrition", displayName = "Харчування").apply { id = categoryId }
        whenever(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category))
        whenever(embeddingProvider.generateEmbedding(any())).thenReturn(null)

        assertThrows<RuntimeException> {
            service.uploadArticle("Title", "Content", categoryId)
        }
    }

    @Test
    fun `uploadArticleFromFile should process non-PDF content`() {
        val categoryId = UUID.randomUUID()
        val category = MedicalCategory(name = "nutrition", displayName = "Харчування").apply { id = categoryId }
        val content = "Text file content".toByteArray()

        whenever(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category))
        whenever(embeddingProvider.embed(any())).thenReturn(listOf(0.1f, 0.2f, 0.3f))
        whenever(knowledgeRepository.saveAll(any<Iterable<MedicalKnowledge>>())).thenAnswer { (it.arguments[0] as Iterable<MedicalKnowledge>).toList() }

        val results = service.uploadArticleFromFile("From File", content, "notes.txt", categoryId, emptyMap())

        assertEquals(1, results.size)
        assertEquals("From File", results[0].title)
        assertEquals("Text file content", results[0].content)
        verify(embeddingProvider).embed(any())
    }

    @Test
    fun `getArticles should return all articles`() {
        val articles = listOf(
            MedicalKnowledge(title = "Article 1", content = "Content 1"),
            MedicalKnowledge(title = "Article 2", content = "Content 2")
        )
        whenever(knowledgeRepository.findAll()).thenReturn(articles)

        val result = service.getArticles()

        assertEquals(2, result.size)
    }

    @Test
    fun `deleteArticle should soft delete article`() {
        val articleId = UUID.randomUUID()
        val article = MedicalKnowledge(title = "Test", content = "Content").apply { id = articleId }
        whenever(knowledgeRepository.findById(articleId)).thenReturn(Optional.of(article))
        whenever(knowledgeRepository.save(any<MedicalKnowledge>())).thenAnswer { it.arguments[0] }

        service.deleteArticle(articleId)

        assertNotNull(article.deletedAt)
        verify(knowledgeRepository).save(article)
    }

    @Test
    fun `deleteArticle should throw when article not found`() {
        val articleId = UUID.randomUUID()
        whenever(knowledgeRepository.findById(articleId)).thenReturn(Optional.empty())

        assertThrows<RuntimeException> {
            service.deleteArticle(articleId)
        }
    }

    @Test
    fun `getCategories should return all categories`() {
        val categories = listOf(
            MedicalCategory(name = "nutrition", displayName = "Харчування"),
            MedicalCategory(name = "development", displayName = "Розвиток")
        )
        whenever(categoryRepository.findAll()).thenReturn(categories)

        val result = service.getCategories()

        assertEquals(2, result.size)
    }

    @Test
    fun `createCategory should save and return category`() {
        val request = CreateCategoryRequest(name = "test", displayName = "Тест")
        whenever(categoryRepository.save(any<MedicalCategory>())).thenAnswer { it.arguments[0] }

        val result = service.createCategory(request)

        assertEquals("test", result.name)
        assertEquals("Тест", result.displayName)
        verify(categoryRepository).save(any<MedicalCategory>())
    }
}
