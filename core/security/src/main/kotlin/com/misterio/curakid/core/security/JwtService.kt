package com.misterio.curakid.core.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret:defaultSecretKeyWithAtLeast32CharactersLong}") private val secret: String,
    @Value("\${jwt.access-token-expiration:900000}") private val accessExpiration: Long, // 15 min
    @Value("\${jwt.refresh-token-expiration:604800000}") private val refreshExpiration: Long, // 7 days
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateAccessToken(userDetails: UserDetails): String {
        return buildToken(userDetails, accessExpiration, UUID.randomUUID().toString())
    }

    fun generateRefreshToken(userDetails: UserDetails): String {
        return buildToken(userDetails, refreshExpiration)
    }

    private fun buildToken(
        userDetails: UserDetails,
        expiration: Long,
        jti: String? = null
    ): String {
//        val claims = mutableMapOf<String, Any>()
//        if (jti != null) {
//            claims["jti"] = jti
//        }


        return Jwts.builder()
            .id(jti)
//            .claims(claims)
            .subject(userDetails.username)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + expiration))
            .signWith(key)
            .compact()
    }

    fun extractUsername(token: String): String? = try {
        extractClaim(token, Claims::getSubject)
    } catch (e: Exception) {
        null
    }

    fun extractJti(token: String): String? = try {
        extractClaim(token) { it.id }
    } catch (e: Exception) {
        null
    }

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return (username == userDetails.username) && !isTokenExpired(token)
    }

    fun isTokenValid(token: String): Boolean {
        return !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean = try {
        extractExpiration(token)?.before(Date()) ?: true
    } catch (e: Exception) {
        true
    }

    fun extractExpiration(token: String): Date? = extractClaim(token, Claims::getExpiration)

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}