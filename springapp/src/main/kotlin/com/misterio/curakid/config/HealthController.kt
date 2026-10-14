package com.misterio.curakid.config

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: Instant = Instant.now()
)

@RestController
class HealthController {

    @GetMapping("/api/health")
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                service = "curakid-backend"
            )
        )
    }
}
