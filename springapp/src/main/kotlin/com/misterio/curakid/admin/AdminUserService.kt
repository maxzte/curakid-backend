package com.misterio.curakid.admin

import com.misterio.curakid.auth.User
import com.misterio.curakid.auth.UserRepository
import org.springframework.stereotype.Service

@Service
class AdminUserService(
    private val userRepository: UserRepository
) {
    fun getUsers(): List<User> = userRepository.findAll()
}
