package com.misterio.curakid.feature.children

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.children.Gender
import com.misterio.curakid.core.database.event.Event
import com.misterio.curakid.core.database.event.EventRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChildServiceTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var publisher: ApplicationEventPublisher
    @Mock lateinit var eventRepository: EventRepository
    @Mock lateinit var transactionManager: PlatformTransactionManager

    private lateinit var childService: ChildService
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        childService = ChildService(userRepository, childRepository, publisher, eventRepository, transactionManager)
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
        val savedChild = Child(user, "Ivan", LocalDate.of(2022, 1, 1), Gender.MALE, false, null, emptyList(), emptyList(), emptyList(), emptyList()).apply { id = request.id }
        whenever(userRepository.findById(any())).thenReturn(Optional.of(user))
        whenever(childRepository.findById(request.id)).thenReturn(Optional.empty())
        whenever(childRepository.save(any<Child>())).thenReturn(savedChild)

        val result = childService.createChild(user, request)

        verify(childRepository).save(any<Child>())
        Assertions.assertEquals("Ivan", result.name)
    }

    @Test
    fun `getChild should throw when child not found`() {
        val childId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUserId(childId, user.id)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> { childService.getChild(childId, user) }
    }

    @Test
    fun `getChildren should return active mapped responses`() {
        val child1 = Child(user = user, name = "Ivan", birthDate = LocalDate.of(2022, 1, 1), gender = Gender.MALE).apply { id = UUID.randomUUID() }
        val child2 = Child(user = user, name = "Maria", birthDate = LocalDate.of(2023, 5, 10), gender = Gender.FEMALE).apply { id = UUID.randomUUID() }
        whenever(childRepository.findAllByUserIdAndIsActiveTrue(user.id)).thenReturn(listOf(child1, child2))

        val results = childService.getChildren(user)

        Assertions.assertEquals(2, results.size)
        Assertions.assertEquals("Ivan", results[0].name)
    }

    @Test
    fun `updateChild should throw when child does not belong to user and never save`() {
        val childId = UUID.randomUUID()
        whenever(childRepository.findByIdAndUserId(childId, user.id)).thenReturn(Optional.empty())

        val request = UpdateChildRequest(name = "New Name", birthDate = null, gender = null, gestationalWeeks = null, allergies = null, chronicDiseases = null, pastDiseases = null, specialFeatures = null)
        assertThrows<NoSuchElementException> { childService.updateChild(childId, user, request) }
        verify(childRepository, never()).save(any())
    }

    @Test
    fun `updateChild should update fields and save`() {
        val childId = UUID.randomUUID()
        val existingChild = Child(user = user, name = "Old Name", birthDate = LocalDate.of(2022, 1, 1), gender = Gender.MALE).apply { id = childId }
        whenever(childRepository.findByIdAndUserId(childId, user.id)).thenReturn(Optional.of(existingChild))
        whenever(childRepository.save(any<Child>())).thenAnswer { it.arguments[0] }

        val request = UpdateChildRequest(name = "New Name", birthDate = null, gender = null, gestationalWeeks = null, allergies = listOf("Peanuts"), chronicDiseases = null, pastDiseases = null, specialFeatures = null)
        val result = childService.updateChild(childId, user, request)

        Assertions.assertEquals("New Name", result.name)
        Assertions.assertEquals(listOf("Peanuts"), existingChild.allergies)
        verify(childRepository).save(existingChild)
    }

    @Test
    fun `deleteChild should set isActive to false, anonymize name, clear notes, populate deletedAt, cascade events and save`() {
        val childId = UUID.randomUUID()
        val existingChild = Child(
            user = user,
            name = "Ivan",
            birthDate = LocalDate.of(2022, 1, 1),
            gender = Gender.MALE,
            notes = listOf("Private medical note")
        ).apply { id = childId }
        val event = Event(child = existingChild, title = "Doctor visit")
        whenever(childRepository.findByIdAndUserId(childId, user.id)).thenReturn(Optional.of(existingChild))
        whenever(eventRepository.findAllByChild(existingChild)).thenReturn(listOf(event))

        childService.deleteChild(childId, user)

        val captor = argumentCaptor<Child>()
        verify(childRepository).save(captor.capture())
        val saved = captor.firstValue

        Assertions.assertFalse(saved.isActive)
        Assertions.assertEquals("Deleted Child", saved.name)
        Assertions.assertTrue(saved.notes.isEmpty())
        Assertions.assertNotNull(saved.deletedAt)

        verify(eventRepository).saveAll(any<List<Event>>())
        Assertions.assertNotNull(event.deletedAt)
    }
}
