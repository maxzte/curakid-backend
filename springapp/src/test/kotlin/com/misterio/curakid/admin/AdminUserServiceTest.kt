package com.misterio.curakid.admin

import com.misterio.curakid.admin.service.AdminUserService
import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.core.database.children.Child
import com.misterio.curakid.core.database.children.ChildRepository
import com.misterio.curakid.core.database.notification.DeviceTokenRepository
import com.misterio.curakid.core.database.token.RefreshTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.PlatformTransactionManager
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var childRepository: ChildRepository
    @Mock lateinit var refreshTokenRepository: RefreshTokenRepository
    @Mock lateinit var deviceTokenRepository: DeviceTokenRepository
    @Mock lateinit var transactionManager: PlatformTransactionManager
    private lateinit var service: AdminUserService

    @BeforeEach
    fun setUp() {
        service = AdminUserService(userRepository, childRepository, refreshTokenRepository, deviceTokenRepository, null, transactionManager)
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
    fun `deleteUser should anonymize user and children with deletedAt populated`() {
        val userId = UUID.randomUUID()
        val user = User(email = "original@test.com", name = "Original User").apply { id = userId }
        val child1 = Child(name = "Child 1").apply { id = UUID.randomUUID() }
        val child2 = Child(name = "Child 2").apply { id = UUID.randomUUID() }

        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(childRepository.findAllByUser(user)).thenReturn(listOf(child1, child2))
        whenever(childRepository.save(any<Child>())).thenAnswer { it.arguments[0] }
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] }

        service.deleteUser(userId)

        val childCaptor = argumentCaptor<Child>()
        verify(childRepository, times(2)).save(childCaptor.capture())
        childCaptor.allValues.forEach { savedChild ->
            assertEquals("Deleted Child", savedChild.name)
            assertFalse(savedChild.isActive)
            assertNotNull(savedChild.deletedAt)
        }

        val userCaptor = argumentCaptor<User>()
        verify(userRepository).save(userCaptor.capture())
        val savedUser = userCaptor.firstValue
        assertTrue(savedUser.email.startsWith("deleted_"))
        assertEquals("Deleted User", savedUser.name)
        assertFalse(savedUser.isActive)
        assertNotNull(savedUser.deletedAt)
    }

    @Test
    fun `deleteUser should throw when user not found and never save`() {
        val userId = UUID.randomUUID()
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertThrows<NoSuchElementException> {
            service.deleteUser(userId)
        }
        verify(userRepository, never()).save(any())
        verify(childRepository, never()).save(any())
    }
}
