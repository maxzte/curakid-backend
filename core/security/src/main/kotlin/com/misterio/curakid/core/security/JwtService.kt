package com.misterio.curakid.core.security

import com.misterio.curakid.core.database.auth.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value($$"${jwt.secret}") private val secret: String,
    @Value($$"${jwt.access-token-expiration}") private val accessExpiration: Long,
    @Value($$"${jwt.refresh-token-expiration}") val refreshExpiration: Long,
) {
    companion object {
        const val CLAIM_TOKEN_TYPE = "type"
        const val CLAIM_USER_ID = "uid"
        const val CLAIM_ROLE = "role"
        const val TYPE_ACCESS = "access"
        const val TYPE_REFRESH = "refresh"
        const val ISSUER = "curakid-api"
        private val log = LoggerFactory.getLogger(JwtService::class.java)
    }

    sealed class JwtResult {
        data class Access(val claims: Claims) : JwtResult()
        data class Refresh(val claims: Claims) : JwtResult()
        object Expired : JwtResult()
        object Invalid : JwtResult()
    }

    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    private val jwtParser: JwtParser = Jwts.parser()
        .verifyWith(key)
        .requireIssuer(ISSUER)
        .build()

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
            .issuer(ISSUER)
            .subject(userDetails.username)
            .claim(CLAIM_TOKEN_TYPE, tokenType)
            .issuedAt(Date(now))
            .expiration(Date(now + expiration))
            .signWith(key)

        if (userDetails is User) {
            builder.claim(CLAIM_USER_ID, userDetails.id.toString())
            builder.claim(CLAIM_ROLE, userDetails.role.name)
        } else {
            val roleAuthority = userDetails.authorities.firstOrNull()?.authority
            if (roleAuthority != null) {
                val roleName = if (roleAuthority.startsWith("ROLE_")) roleAuthority.substring(5) else roleAuthority
                builder.claim(CLAIM_ROLE, roleName)
            }
        }

        if (jti != null) {
            builder.id(jti)
        }

        return builder.compact()
    }

    fun parseToken(token: String): JwtResult {
        return try {
            val claims = jwtParser.parseSignedClaims(token).payload
            val type = claims.get(CLAIM_TOKEN_TYPE, String::class.java)
            val hasSubject = claims.subject != null
            val hasJti = claims.id != null
            val hasExp = claims.expiration != null

            if (!hasSubject || !hasJti || !hasExp) {
                log.debug("JWT rejected: missing required claims (subject, id, or expiration)")
                return JwtResult.Invalid
            }

            when (type) {
                TYPE_ACCESS -> JwtResult.Access(claims)
                TYPE_REFRESH -> JwtResult.Refresh(claims)
                else -> {
                    log.debug("JWT rejected: unknown token type '{}'", type)
                    JwtResult.Invalid
                }
            }
        } catch (e: ExpiredJwtException) {
            log.debug("JWT expired: {}", e.message)
            JwtResult.Expired
        } catch (e: JwtException) {
            log.debug("JWT rejected: {} - {}", e.javaClass.simpleName, e.message)
            JwtResult.Invalid
        } catch (e: IllegalArgumentException) {
            log.debug("JWT rejected (invalid argument): {}", e.message)
            JwtResult.Invalid
        }
    }

    fun getClaimsIgnoringExpiration(token: String): Claims? {
        return try {
            jwtParser.parseSignedClaims(token).payload
        } catch (e: ExpiredJwtException) {
            e.claims
        } catch (_: Exception) {
            null
        }
    }
}
