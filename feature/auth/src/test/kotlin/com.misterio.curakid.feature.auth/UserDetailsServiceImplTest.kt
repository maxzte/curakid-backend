package com.misterio.curakid.feature.auth

import com.misterio.curakid.core.database.auth.User
import com.misterio.curakid.core.database.auth.UserRepository
import com.misterio.curakid.feature.auth.service.UserDetailsServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserDetailsServiceImplTest {

    @Mock lateinit var userRepository: UserRepository
    private lateinit var service: UserDetailsServiceImpl

    @BeforeEach
    fun setUp() {
        service = UserDetailsServiceImpl(userRepository)
    }

    @Test
    fun `loadUserByUsername should return user when found`() {
        val user = User(email = "test@example.com")
        whenever(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user))

        val result = service.loadUserByUsername("test@example.com")

        assertEquals(user, result)
    }

    @Test
    fun `loadUserByUsername should throw when not found`() {
        whenever(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty())

        assertThrows<UsernameNotFoundException> {
            service.loadUserByUsername("unknown@example.com")
        }
    }
}
