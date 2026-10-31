package com.misterio.curakid.config

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FaviconController {

    @GetMapping("favicon.ico")
    fun returnFavicon(): ResponseEntity<Void> {
        return ResponseEntity.noContent().build()
    }
}
