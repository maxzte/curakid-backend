package com.misterio.curakid.auth

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager,
    private val tokenBlacklistService: TokenBlacklistService
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw RuntimeException("Email already exists")
        }

        val user = User(
            email = request.email,
            passwordHash = passwordEncoder.encode(request.password) ?: "",
            name = request.name,
            isActive = true 
        )
        userRepository.save(user)

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, refreshToken)
    }

    @Transactional
    fun login(request: LoginRequest): AuthResponse {
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(request.email, request.password)
        )

        val user = userRepository.findByEmail(request.email)
            .orElseThrow { RuntimeException("User not found") }

        val accessToken = jwtService.generateAccessToken(user)
        val refreshToken = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, refreshToken)
    }

    @Transactional
    fun refreshToken(request: RefreshTokenRequest): AuthResponse {
        val refreshToken = refreshTokenRepository.findByToken(request.refreshToken)
            .orElseThrow { RuntimeException("Refresh token not found") }

        if (refreshToken.expiryDate < Instant.now()) {
            refreshTokenRepository.delete(refreshToken)
            throw RuntimeException("Refresh token expired")
        }

        val user = refreshToken.user!!
        val accessToken = jwtService.generateAccessToken(user)
        val newRefreshToken = generateAndSaveRefreshToken(user)

        return AuthResponse(accessToken, newRefreshToken)
    }

    @Transactional
    fun logout(authHeader: String?, refreshToken: String) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val jwt = authHeader.substring(7)
            val jti = jwtService.extractJti(jwt)
            val expiry = jwtService.extractClaim(jwt) { it.expiration }.toInstant()
            
            if (jti != null) {
                tokenBlacklistService.addToBlacklist(jti, expiry)
            }
        }
        
        refreshTokenRepository.findByToken(refreshToken).ifPresent {
            refreshTokenRepository.delete(it)
        }
    }

    private fun generateAndSaveRefreshToken(user: User): String {
        refreshTokenRepository.deleteByUser(user)
        refreshTokenRepository.flush()
        val tokenStr = jwtService.generateRefreshToken(user)
        // Using fixed 7 days for now to match default property
        val refreshToken = RefreshToken(
            user = user,
            token = tokenStr,
            expiryDate = Instant.now().plusMillis(604800000)
        )
        refreshTokenRepository.save(refreshToken)
        return tokenStr
    }
}
