package com.misterio.curakid.children

import com.misterio.curakid.auth.User
import com.misterio.curakid.vaccine.VaccineService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDate
import java.util.*

class ChildServiceTest {

    private lateinit var childRepository: ChildRepository
    private lateinit var vaccineService: VaccineService
    private lateinit var childService: ChildService
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        childRepository = mock()
        childService = ChildService(childRepository, vaccineService)
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
        assertEquals("Ivan", result.name)
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

        assertEquals(2, results.size)
        assertEquals("Ivan", results[0].name)
    }

    @Test
    fun `deleteChild should throw when child does not belong to user`() {
        val childId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            childService.deleteChild(childId, user)
        }
    }
}
