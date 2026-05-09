package com.misterio.curakid.feature.profile.presentation

import com.misterio.curakid.feature.profile.domain.UserEntity
import com.misterio.curakid.feature.profile.domain.UserId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/profile")
class ProfileController(private val service: ProfileService) {

    @PostMapping("/register")
    fun createUser(@RequestBody user: UserEntity): UserEntity {
        return service.save(user)
    }

    @GetMapping("/{id}/{email}")
    fun getProfile(@PathVariable id: Long, @PathVariable email: String): String {
        val userId = UserId(id, email)
        return service.getProfile(userId).toString()
    }

    @GetMapping("/all")
    fun getAll(): List<UserEntity> {
        return service.getAll()
    }

}
