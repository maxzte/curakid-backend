package com.misterio.curakid.common

import com.misterio.curakid.auth.User
import org.springframework.security.core.Authentication

fun Authentication.currentUser(): User = this.principal as User
