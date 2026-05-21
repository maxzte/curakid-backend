package com.misterio.curakid.children

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.feature.children.ChildService
import com.misterio.curakid.feature.children.CreateChildRequest
import com.misterio.curakid.feature.children.UpdateChildRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.util.*

class ChildServiceTest {

    private lateinit var childRepository: ChildRepository
    private lateinit var publisher: ApplicationEventPublisher
    private lateinit var childService: ChildService
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        childRepository = mock()
        publisher = mock()
        childService = ChildService(childRepository, publisher)
        user = User(email = "test@test.com", passwordHash = "hash", name = "Test User").apply {
            id = UUID.randomUUID()
        }
    }

    @Test
    fun `createChild should save and return child response`() {
        val request = CreateChildRequest(
            name = "Ivan",
            birthDate = LocalDate.of(2022, 1, 1),
            gender = Gender.MALE,
            isPremature = false,
        )
        val savedChild = Child(user, "Ivan", LocalDate.of(2022, 1, 1), Gender.MALE).apply {
            id = UUID.randomUUID()
        }
        whenever(childRepository.save(any<Child>())).thenReturn(savedChild)

        val result = childService.createChild(user, request)

        verify(childRepository).save(any<Child>())
        Assertions.assertEquals("Ivan", result.name)
    }

    @Test
    fun `getChild should throw when child not found`() {
        val childId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            childService.getChild(childId, user)
        }
    }

    @Test
    fun `getChildren should return mapped responses`() {
        val child1 = Child(user, name = "Ivan", birthDate = LocalDate.of(2022, 1, 1), gender = Gender.MALE).apply {
            id = UUID.randomUUID()
        }
        val child2 = Child(user, name = "Maria", birthDate = LocalDate.of(2023, 5, 10), gender = Gender.FEMALE).apply {
            id = UUID.randomUUID()
        }
        whenever(childRepository.findAllByUser(user)).thenReturn(listOf(child1, child2))

        val results = childService.getChildren(user)

        Assertions.assertEquals(2, results.size)
        Assertions.assertEquals("Ivan", results[0].name)
    }

    @Test
    fun `deleteChild should throw when child does not belong to user`() {
        val childId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            childService.deleteChild(childId, user)
        }
    }

    @Test
    fun `updateChild should update fields and save`() {
        val childId = UUID.randomUUID()
        val existingChild = Child(user, "Old Name", LocalDate.of(2022, 1, 1), Gender.MALE).apply {
            id = childId
        }
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.of(existingChild))
        whenever(childRepository.save(any<Child>())).thenAnswer { it.arguments[0] }

        val request = UpdateChildRequest(
            name = "New Name",
            birthDate = null,
            gender = null,
            allergies = listOf("Peanuts"),
            chronicDiseases = null,
            pastDiseases = null,
            specialFeatures = null
        )
        val result = childService.updateChild(childId, user, request)

        Assertions.assertEquals("New Name", result.name)
        Assertions.assertEquals(listOf("Peanuts"), existingChild.allergies)
        verify(childRepository).save(existingChild)
    }

    @Test
    fun `deleteChild should call repository delete`() {
        val childId = UUID.randomUUID()
        val existingChild = Child(user, "Ivan", LocalDate.of(2022, 1, 1), Gender.MALE).apply {
            id = childId
        }
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.of(existingChild))

        childService.deleteChild(childId, user)

        verify(childRepository).delete(existingChild)
    }
}