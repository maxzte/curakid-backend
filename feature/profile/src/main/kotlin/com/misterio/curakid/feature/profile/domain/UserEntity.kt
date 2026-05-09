package com.misterio.curakid.feature.profile.domain

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import java.io.Serializable

@Entity
data class UserEntity(

    @EmbeddedId
    val id: UserId,
    val nick: String,
    val password: String,

)

@Embeddable
data class UserId (
    val id: Long,
    val email: String,
) : Serializable
