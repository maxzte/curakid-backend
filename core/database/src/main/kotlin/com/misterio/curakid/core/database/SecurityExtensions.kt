package com.misterio.curakid.core.database

import com.misterio.curakid.core.database.auth.User
import org.springframework.security.core.Authentication

fun Authentication.currentUser(): User = this.principal as User
