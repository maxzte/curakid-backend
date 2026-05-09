package com.misterio.curakid.feature.profile.presentation

import com.misterio.curakid.feature.profile.domain.ProfileRepo
import com.misterio.curakid.feature.profile.domain.UserEntity
import com.misterio.curakid.feature.profile.domain.UserId
import org.springframework.stereotype.Service


@Service
class ProfileService(private val repo: ProfileRepo) {

    fun save(profile: UserEntity) = repo.save(profile)

    fun getProfile(id: UserId) = repo.findById(id)

    fun getAll() = repo.findAll()

}
