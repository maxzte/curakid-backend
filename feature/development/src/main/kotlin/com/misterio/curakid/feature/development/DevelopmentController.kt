package com.misterio.curakid.feature.development

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/development")
class DevelopmentController(
    private val lifePeriodQuestionService: LifePeriodQuestionService
) {

    @GetMapping("/life-periods")
    fun getAllPeriods(): ResponseEntity<List<LifePeriodResponse>> {
        return ResponseEntity.ok(lifePeriodQuestionService.getAllPeriodsWithQuestions())
    }

    @GetMapping("/life-periods/{code}")
    fun getPeriodByCode(@PathVariable code: String): ResponseEntity<LifePeriodResponse> {
        return ResponseEntity.ok(lifePeriodQuestionService.getPeriodWithQuestions(code))
    }

}
