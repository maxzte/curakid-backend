package com.misterio.curakid.core.security

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager

import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig(
    private val adminUiAuthFilter: AdminUiAuthFilter,
    private val rateLimitFilter: RateLimitFilter,
    private val jwtAuthFilter: JwtAuthenticationFilter,
    private val internalApiKeyFilter: InternalApiKeyFilter,
    private val env: Environment,
    @Value($$"${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
    private val allowedOriginsStr: String = "http://localhost:3000,http://localhost:5173,http://localhost:8080"
) {

    @Bean
    open fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        val origins = allowedOriginsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
        configuration.allowedOriginPatterns = if (origins.contains("*")) listOf("*") else origins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = !origins.contains("*")
        configuration.maxAge = 3600L

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    open fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager {
        return config.authenticationManager
    }

    @Bean
    open fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    open fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val isDev = env.activeProfiles.any { it == "local" || it == "dev" || it == "test" }

        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
                headers.contentTypeOptions { }
                headers.xssProtection { }
                headers.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true).maxAgeInSeconds(31536000)
                }
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                }
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/legal", "/api/legal/**", "/api/v1/legal", "/api/v1/legal/**").permitAll()
                auth.requestMatchers("/api/health", "/actuator/health", "/actuator/health/**").permitAll()
                auth.requestMatchers("/api/v1/notifications/test-push", "/api/v1/notifications/trigger-reminders").permitAll()
                auth.requestMatchers("/actuator/**").hasRole("ADMIN")
                if (isDev) {
                    auth.requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).permitAll()
                } else {
                    auth.requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    ).denyAll()
                }
                auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                auth.requestMatchers("/api/auth/**", "/api/v1/auth/**").permitAll()
                auth.requestMatchers("/admin/ui/login").permitAll()
                auth.requestMatchers("/admin/ui", "/admin/ui/**").hasRole("ADMIN")
                auth.requestMatchers("/css/**", "/js/**", "/images/**", "/admin/js/**", "/admin/css/**", "/favicon.ico").permitAll()
                auth.requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(adminUiAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}