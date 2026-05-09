package com.misterio.curakid.development

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/development")
class DevelopmentController(
    private val lifePeriodQuestionService: LifePeriodQuestionService
) {

    @GetMapping("/life-periods")
    fun getAllPeriods(): ResponseEntity<List<PeriodDTO>> {
        return ResponseEntity.ok(lifePeriodQuestionService.getAllPeriodsWithQuestions())
    }

    @GetMapping("/life-periods/{code}")
    fun getPeriodByCode(@PathVariable code: String): ResponseEntity<PeriodDTO> {
        return ResponseEntity.ok(lifePeriodQuestionService.getPeriodWithQuestions(code))
    }
}
