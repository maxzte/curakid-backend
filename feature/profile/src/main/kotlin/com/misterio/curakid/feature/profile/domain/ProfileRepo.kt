package com.misterio.curakid.feature.profile.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository


@Repository
interface ProfileRepo : JpaRepository<UserEntity, UserId>
