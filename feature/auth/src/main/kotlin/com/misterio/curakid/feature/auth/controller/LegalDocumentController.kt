package com.misterio.curakid.feature.auth.controller

import com.misterio.curakid.core.database.auth.ConsentType
import com.misterio.curakid.feature.auth.LegalDocumentResponse
import com.misterio.curakid.feature.auth.service.LegalDocumentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/legal")
class LegalDocumentController(
    private val service: LegalDocumentService
) {
    @GetMapping
    fun getAll(): ResponseEntity<List<LegalDocumentResponse>> {
        return ResponseEntity.ok(service.getActiveDocuments())
    }

    @GetMapping("/{type}")
    fun getByType(
        @PathVariable type: ConsentType
    ): ResponseEntity<LegalDocumentResponse> {
        return ResponseEntity.ok(service.getActiveDocument(type))
    }

}
