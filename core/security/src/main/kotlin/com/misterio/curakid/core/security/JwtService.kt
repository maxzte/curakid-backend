package com.misterio.curakid.core.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtParser
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
    @Value("\${jwt.refresh-token-expiration:604800000}") val refreshExpiration: Long, // 7 days
) {
    companion object {
        const val CLAIM_TOKEN_TYPE = "type"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
    }

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    private val jwtParser: JwtParser by lazy {
        Jwts.parser().verifyWith(key).build()
    }

    fun generateAccessToken(userDetails: UserDetails): String {
        return buildToken(userDetails, accessExpiration, UUID.randomUUID().toString(), TYPE_ACCESS)
    }

    fun generateRefreshToken(userDetails: UserDetails): String {
        return buildToken(userDetails, refreshExpiration, UUID.randomUUID().toString(), TYPE_REFRESH)
    }

    private fun buildToken(
        userDetails: UserDetails,
        expiration: Long,
        jti: String? = null,
        tokenType: String
    ): String {
        val now = System.currentTimeMillis()
        val builder = Jwts.builder()
            .subject(userDetails.username)
            .claim(CLAIM_TOKEN_TYPE, tokenType)
            .issuedAt(Date(now))
            .expiration(Date(now + expiration))
            .signWith(key)

        if (jti != null) {
            builder.id(jti)
        }

        return builder.compact()
    }

    fun parseValidClaims(token: String): Claims? = try {
        val claims = jwtParser.parseSignedClaims(token).payload
        if (claims.expiration?.before(Date()) == true) null else claims
    } catch (_: Exception) {
        null
    }

    fun extractUsername(token: String): String? = try {
        extractClaim(token, Claims::getSubject)
    } catch (_: Exception) {
        null
    }

    fun extractJti(token: String): String? = try {
        extractClaim(token) { it.id }
    } catch (_: Exception) {
        null
    }

    fun extractTokenType(token: String): String? = try {
        extractClaim(token) { it.get(CLAIM_TOKEN_TYPE, String::class.java) }
    } catch (_: Exception) {
        null
    }

    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val claims = parseValidClaims(token) ?: return false
        return (claims.subject == userDetails.username)
    }

    fun isTokenValid(token: String): Boolean {
        return parseValidClaims(token) != null
    }

    fun extractExpiration(token: String): Date? = extractClaim(token, Claims::getExpiration)

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims {
        return jwtParser.parseSignedClaims(token).payload
    }
}