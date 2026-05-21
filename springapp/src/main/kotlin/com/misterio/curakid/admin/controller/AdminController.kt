package com.misterio.curakid.admin.controller

import com.misterio.curakid.admin.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val knowledgeService: AdminKnowledgeService,
    private val quizService: AdminQuizService,
    private val fuzzyService: AdminFuzzyService,
    private val userService: AdminUserService,
    private val vaccineService: AdminVaccineService
) {

    // Knowledge
    @PostMapping("/knowledge")
    fun uploadArticle(@RequestBody request: CreateArticleRequest) =
        ResponseEntity.ok(knowledgeService.uploadArticle(request.title, request.content, request.categoryId))

    @GetMapping("/knowledge")
    fun getArticles() = ResponseEntity.ok(knowledgeService.getArticles())

    @DeleteMapping("/knowledge/{id}")
    fun deleteArticle(@PathVariable id: UUID): ResponseEntity<Void> {
        knowledgeService.deleteArticle(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/knowledge/categories")
    fun getCategories() = ResponseEntity.ok(knowledgeService.getCategories())

    @PostMapping("/knowledge/categories")
    fun createCategory(@RequestBody request: CreateCategoryRequest) =
        ResponseEntity.ok(knowledgeService.createCategory(request))

    // Quiz — Life Periods
    @GetMapping("/quiz/life-periods")
    fun getLifePeriods() = ResponseEntity.ok(quizService.getLifePeriods())

    @PostMapping("/quiz/life-periods")
    fun createLifePeriod(@RequestBody request: CreateLifePeriodRequest) =
        ResponseEntity.ok(quizService.createLifePeriod(request))

    @PutMapping("/quiz/life-periods/{id}")
    fun updateLifePeriod(
        @PathVariable id: UUID,
        @RequestBody request: CreateLifePeriodRequest
    ) = ResponseEntity.ok(quizService.updateLifePeriod(id, request))

    @DeleteMapping("/quiz/life-periods/{id}")
    fun deleteLifePeriod(@PathVariable id: UUID): ResponseEntity<Void> {
        quizService.deleteLifePeriod(id)
        return ResponseEntity.noContent().build()
    }

    // Quiz — Questions
    @PostMapping("/quiz/life-periods/{lifePeriodId}/questions")
    fun createQuestion(
        @PathVariable lifePeriodId: UUID,
        @RequestBody request: CreateQuizQuestionRequest
    ) = ResponseEntity.ok(quizService.createQuestion(lifePeriodId, request))

    @PutMapping("/quiz/questions/{id}")
    fun updateQuestion(
        @PathVariable id: UUID,
        @RequestBody request: CreateQuizQuestionRequest
    ) = ResponseEntity.ok(quizService.updateQuestion(id, request))

    @DeleteMapping("/quiz/questions/{id}")
    fun deleteQuestion(@PathVariable id: UUID): ResponseEntity<Void> {
        quizService.deleteQuestion(id)
        return ResponseEntity.noContent().build()
    }

    // Quiz — Answer Options
    @PostMapping("/quiz/questions/{questionId}/options")
    fun createAnswerOption(
        @PathVariable questionId: UUID,
        @RequestBody request: CreateAnswerOptionRequest
    ) = ResponseEntity.ok(quizService.createAnswerOption(questionId, request))

    @PutMapping("/quiz/options/{id}")
    fun updateAnswerOption(
        @PathVariable id: UUID,
        @RequestBody request: CreateAnswerOptionRequest
    ) = ResponseEntity.ok(quizService.updateAnswerOption(id, request))

    @DeleteMapping("/quiz/options/{id}")
    fun deleteAnswerOption(@PathVariable id: UUID): ResponseEntity<Void> {
        quizService.deleteAnswerOption(id)
        return ResponseEntity.noContent().build()
    }

    // Fuzzy Logic
    @GetMapping("/fuzzy/rules")
    fun getRules() = ResponseEntity.ok(fuzzyService.getRules())

    @PutMapping("/fuzzy/rules")
    fun updateRules(@RequestBody request: UpdateRulesRequest) =
        ResponseEntity.ok(fuzzyService.updateRules(request.rules))

    // Users
    @GetMapping("/users")
    fun getUsers(): ResponseEntity<List<AdminUserResponse>> {
        val users = userService.getUsers()
            .map {
                AdminUserResponse(it.id!!, it.email, it.name, it.role.name, it.isActive)
            }
        return ResponseEntity.ok(users)
    }

    // Vaccines
    @GetMapping("/vaccines")
    fun getVaccines() = ResponseEntity.ok(vaccineService.getTemplates())

    @PostMapping("/vaccines")
    fun createVaccine(@RequestBody request: CreateVaccineTemplateRequest) =
        ResponseEntity.ok(vaccineService.createTemplate(request))

    @PutMapping("/vaccines/{id}")
    fun updateVaccine(
        @PathVariable id: UUID,
        @RequestBody request: CreateVaccineTemplateRequest
    ) = ResponseEntity.ok(vaccineService.updateTemplate(id, request))

    @DeleteMapping("/vaccines/{id}")
    fun deleteVaccine(@PathVariable id: UUID): ResponseEntity<Void> {
        vaccineService.deleteTemplate(id)
        return ResponseEntity.noContent().build()
    }
}
