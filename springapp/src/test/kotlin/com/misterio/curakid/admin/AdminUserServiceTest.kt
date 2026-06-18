package com.misterio.curakid.admin

import com.misterio.curakid.admin.service.AdminUserService
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdminUserServiceTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var childRepository: ChildRepository
    private lateinit var service: AdminUserService

    @BeforeEach
    fun setUp() {
        service = AdminUserService(userRepository, childRepository)
    }

    @Test
    fun `getUsers should return paginated users when no email filter`() {
        val pageable = PageRequest.of(0, 10, Sort.by("email").ascending())
        val users = listOf(User(email = "a@test.com"), User(email = "b@test.com"))
        whenever(userRepository.findAll(pageable)).thenReturn(PageImpl(users))

        val result = service.getUsers(email = null, page = 0, size = 10)

        assertEquals(2, result.content.size)
        verify(userRepository).findAll(pageable)
    }

    @Test
    fun `getUsers should filter by email when email param provided`() {
        val pageable = PageRequest.of(0, 10, Sort.by("email").ascending())
        val users = listOf(User(email = "test@example.com"))
        whenever(userRepository.findByEmailContainingIgnoreCase("test", pageable)).thenReturn(PageImpl(users))

        val result = service.getUsers(email = "test", page = 0, size = 10)

        assertEquals(1, result.content.size)
    }

    @Test
    fun `getUsers should return all when email is blank`() {
        val pageable = PageRequest.of(0, 10, Sort.by("email").ascending())
        whenever(userRepository.findAll(pageable)).thenReturn(PageImpl(emptyList()))

        val result = service.getUsers(email = "", page = 0, size = 10)

        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `deleteUser should anonymize user and children`() {
        val userId = UUID.randomUUID()
        val user = User(email = "original@test.com", name = "Original User").apply { id = userId }
        val child1 = Child(name = "Child 1").apply { id = UUID.randomUUID() }
        val child2 = Child(name = "Child 2").apply { id = UUID.randomUUID() }

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(childRepository.findAllByUser(user)).thenReturn(listOf(child1, child2))
        whenever(childRepository.save(any<Child>())).thenAnswer { it.arguments[0] }
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        service.deleteUser(userId)

        assertEquals("Deleted Child", child1.name)
        assertEquals("Deleted Child", child2.name)
        assertTrue(user.email.startsWith("deleted_"))
        assertEquals("Deleted User", user.name)
        assertFalse(user.isActive)
        verify(childRepository).save(child1)
        verify(childRepository).save(child2)
        verify(userRepository).save(user)
    }

    @Test
    fun `deleteUser should throw when user not found`() {
        val userId = UUID.randomUUID()
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            service.deleteUser(userId)
        }
    }
}
