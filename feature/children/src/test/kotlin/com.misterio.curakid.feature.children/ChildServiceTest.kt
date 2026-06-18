package com.misterio.curakid.feature.children

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ChildServiceTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var publisher: ApplicationEventPublisher

    private lateinit var childService: ChildService
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        childService = ChildService(userRepository, childRepository, publisher)
        user = User(email = "test@test.com", passwordHash = "hash", name = "Test User").apply { id = UUID.randomUUID() }
    }

    @Test
    fun `createChild should save and return child response`() {
        val request = CreateChildRequest(
            id = UUID.randomUUID(),
            name = "Ivan",
            birthDate = LocalDate.of(2022, 1, 1),
            gender = Gender.MALE,
            isPremature = false,
            gestationalWeeks = null,
            allergies = null,
            chronicDiseases = null,
            pastDiseases = null,
            specialFeatures = null,
        )
        val savedChild = Child(user, "Ivan", LocalDate.of(2022, 1, 1), Gender.MALE, false, null, emptyList(), emptyList(), emptyList(), emptyList()).apply { id = UUID.randomUUID() }
        whenever(userRepository.findById(any())).thenReturn(Optional.of(user))
        whenever(childRepository.save(any<Child>())).thenReturn(savedChild)

        val result = childService.createChild(user, request)

        verify(childRepository).save(any<Child>())
        Assertions.assertEquals("Ivan", result.name)
    }

    @Test
    fun `getChild should throw when child not found`() {
        val childId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> { childService.getChild(childId, user) }
    }

    @Test
    fun `getChildren should return mapped responses`() {
        val child1 = Child(user = user, name = "Ivan", birthDate = LocalDate.of(2022, 1, 1), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val child2 = Child(user = user, name = "Maria", birthDate = LocalDate.of(2023, 5, 10), gender = Gender.FEMALE).apply { id = UUID.randomUUID() }
        whenever(childRepository.findAllByUser(user)).thenReturn(listOf(child1, child2))

        val results = childService.getChildren(user)

        Assertions.assertEquals(2, results.size)
        Assertions.assertEquals("Ivan", results[0].name)
    }

    @Test
    fun `deleteChild should throw when child does not belong to user`() {
        val childId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> { childService.deleteChild(childId, user) }
    }

    @Test
    fun `updateChild should update fields and save`() {
        val childId = UUID.randomUUID()
        val existingChild = Child(user = user, name = "Old Name", birthDate = LocalDate.of(2022, 1, 1), gender = Gender.MALE).apply { id = childId }
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.of(existingChild))
        whenever(childRepository.save(any<Child>())).thenAnswer { it.arguments[0] }

        val request = UpdateChildRequest(name = "New Name", birthDate = null, gender = null, gestationalWeeks = null, allergies = listOf("Peanuts"), chronicDiseases = null, pastDiseases = null, specialFeatures = null)
        val result = childService.updateChild(childId, user, request)

        Assertions.assertEquals("New Name", result.name)
        Assertions.assertEquals(listOf("Peanuts"), existingChild.allergies)
        verify(childRepository).save(existingChild)
    }

    @Test
    fun `deleteChild should set isActive to false, anonymize name and save`() {
        val childId = UUID.randomUUID()
        val existingChild = Child(user = user, name = "Ivan", birthDate = LocalDate.of(2022, 1, 1), gender = Gender.MALE).apply { id = childId }
        whenever(childRepository.findByIdAndUser(childId, user)).thenReturn(Optional.of(existingChild))

        childService.deleteChild(childId, user)

        Assertions.assertFalse(existingChild.isActive)
        Assertions.assertEquals("Deleted Child", existingChild.name)
        verify(childRepository).save(existingChild)
    }
}
