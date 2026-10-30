package com.misterio.curakid.admin.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.misterio.curakid.admin.*
import com.misterio.curakid.admin.service.*
import com.misterio.curakid.feature.development.LifePeriodQuestionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val knowledgeService: AdminKnowledgeService,
    private val quizService: AdminQuizService,
    private val fuzzyService: AdminFuzzyService,
    private val userService: AdminUserService,
    private val vaccineService: AdminVaccineService,
    private val legalService: AdminLegalService,
    private val objectMapper: ObjectMapper
) {

    // Knowledge
    @PostMapping("/knowledge")
    fun uploadArticle(@Valid @RequestBody request: CreateArticleRequest) =
        ResponseEntity.ok(knowledgeService.uploadArticle(request.title, request.content, request.categoryId))

    @PostMapping("/knowledge/upload", consumes = ["multipart/form-data"])
    fun uploadArticleFile(
        @RequestParam("title") title: String,
        @RequestParam("categoryId") categoryId: UUID,
        @RequestParam("file") file: MultipartFile,
        @RequestParam("minAge", required = false) minAge: Int?,
        @RequestParam("maxAge", required = false) maxAge: Int?
    ): ResponseEntity<List<AdminArticleResponse>> {
        val metadata = mutableMapOf<String, Any>()
        if (minAge != null || maxAge != null) {
            val ageMap = mutableMapOf<String, Int>()
            minAge?.let { ageMap["min"] = it }
            maxAge?.let { ageMap["max"] = it }
            metadata["age"] = ageMap
        }
        
        return ResponseEntity.ok(knowledgeService.uploadArticleFromFile(title, file.bytes, file.originalFilename ?: "unknown", categoryId, metadata))
    }

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
    fun createCategory(@Valid @RequestBody request: CreateCategoryRequest) =
        ResponseEntity.ok(knowledgeService.createCategory(request))

    // Quiz — Life Periods
    @GetMapping("/quiz/life-periods")
    fun getLifePeriods(): ResponseEntity<List<AdminLifePeriodResponse>> {
        val response = quizService.getLifePeriods().map { lp ->
            val lpId = requireNotNull(lp.id) { "LifePeriod ID must not be null" }
            AdminLifePeriodResponse(
                id = lpId,
                code = lp.code,
                label = lp.label,
                description = LifePeriodQuestionService.parseDescription(lp.description, objectMapper),
                minMonths = lp.minMonths,
                maxMonths = lp.maxMonths,
                questions = lp.questions.filter { it.isActive }.map { q ->
                    val qId = requireNotNull(q.id) { "Question ID must not be null" }
                    AdminQuestionResponse(
                        id = qId,
                        text = q.text,
                        domain = q.domain,
                        weight = q.weight,
                        prerequisite = q.prerequisite?.let { prereq ->
                            val prereqId = requireNotNull(prereq.id) { "Prerequisite ID must not be null" }
                            AdminPrerequisiteResponse(prereqId, prereq.text)
                        },
                        options = q.options.map { o ->
                            val optId = requireNotNull(o.id) { "Option ID must not be null" }
                            AdminOptionResponse(optId, o.text, o.value, o.displayOrder)
                        }
                    )
                }
            )
        }
        return ResponseEntity.ok(response)
    }

    @PostMapping("/quiz/life-periods")
    fun createLifePeriod(@Valid @RequestBody request: CreateLifePeriodRequest): ResponseEntity<AdminLifePeriodResponse> =
        ResponseEntity.ok(quizService.createLifePeriod(request))

    @PutMapping("/quiz/life-periods/{id}")
    fun updateLifePeriod(
        @PathVariable id: UUID,
        @RequestBody request: CreateLifePeriodRequest
    ): ResponseEntity<AdminLifePeriodResponse> = ResponseEntity.ok(quizService.updateLifePeriod(id, request))


    // Quiz — Questions
    @PostMapping("/quiz/life-periods/{lifePeriodId}/questions")
    fun createQuestion(
        @PathVariable lifePeriodId: UUID,
        @Valid @RequestBody request: CreateQuizQuestionRequest
    ): ResponseEntity<AdminQuestionResponse> = ResponseEntity.ok(quizService.createQuestion(lifePeriodId, request))

    @PutMapping("/quiz/questions/{id}")
    fun updateQuestion(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateQuizQuestionRequest
    ): ResponseEntity<AdminQuestionResponse> = ResponseEntity.ok(quizService.updateQuestion(id, request))

    @DeleteMapping("/quiz/questions/{id}")
    fun deleteQuestion(@PathVariable id: UUID): ResponseEntity<Void> {
        quizService.deleteQuestion(id)
        return ResponseEntity.noContent().build()
    }

    // Quiz — Answer Options
    @PostMapping("/quiz/questions/{questionId}/options")
    fun createAnswerOption(
        @PathVariable questionId: UUID,
        @Valid @RequestBody request: CreateAnswerOptionRequest
    ): ResponseEntity<AdminOptionResponse> = ResponseEntity.ok(quizService.createAnswerOption(questionId, request))

    @PutMapping("/quiz/options/{id}")
    fun updateAnswerOption(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateAnswerOptionRequest
    ): ResponseEntity<AdminOptionResponse> = ResponseEntity.ok(quizService.updateAnswerOption(id, request))

    @DeleteMapping("/quiz/options/{id}")
    fun deleteAnswerOption(@PathVariable id: UUID): ResponseEntity<Void> {
        quizService.deleteAnswerOption(id)
        return ResponseEntity.noContent().build()
    }

    // Fuzzy Logic
    @GetMapping("/fuzzy/rules")
    fun getRules(): ResponseEntity<List<AdminFuzzyRuleResponse>> = ResponseEntity.ok(fuzzyService.getRules())

    @PutMapping("/fuzzy/rules")
    fun updateRules(@RequestBody request: UpdateRulesRequest): ResponseEntity<List<AdminFuzzyRuleResponse>> =
        ResponseEntity.ok(fuzzyService.updateRules(request.rules))

    // Users
    @GetMapping("/users")
    fun getUsers(
        @RequestParam(required = false) email: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<AdminPageResponse<AdminUserResponse>> {
        val pageResult = userService.getUsers(email, page, size)
        val response = AdminPageResponse(
            items = pageResult.content.map {
                val userId = requireNotNull(it.id) { "User ID must not be null" }
                AdminUserResponse(userId, it.email, it.name, it.role.name, it.isActive)
            },
            totalItems = pageResult.totalElements,
            totalPages = pageResult.totalPages,
            currentPage = pageResult.number
        )
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/users/{id}")
    fun deleteUser(@PathVariable id: UUID): ResponseEntity<Void> {
        userService.deleteUser(id)
        return ResponseEntity.noContent().build()
    }

    // Vaccines
    @GetMapping("/vaccines")
    fun getVaccines() = ResponseEntity.ok(vaccineService.getTemplates())

    @PostMapping("/vaccines")
    fun createVaccine(@Valid @RequestBody request: CreateVaccineTemplateRequest) =
        ResponseEntity.ok(vaccineService.createTemplate(request))

    @PutMapping("/vaccines/{id}")
    fun updateVaccine(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateVaccineTemplateRequest
    ) = ResponseEntity.ok(vaccineService.updateTemplate(id, request))

    @DeleteMapping("/vaccines/{id}")
    fun deleteVaccine(@PathVariable id: UUID): ResponseEntity<Void> {
        vaccineService.deleteTemplate(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/vaccines/sync/preview")
    fun previewVaccinesSync() = ResponseEntity.ok(vaccineService.previewSync())

    @PostMapping("/vaccines/sync/apply")
    fun applyVaccinesSync(@RequestBody request: com.misterio.curakid.admin.vaccines.ApplyVaccineSyncRequest) =
        ResponseEntity.ok(vaccineService.applySync(request))

    @PostMapping("/vaccines/sync/backfill")
    fun backfillVaccinesForChildren() = ResponseEntity.ok(vaccineService.backfillForExistingChildren())

    // Legal Documents (Policies)
    @GetMapping("/legal")
    fun getLegalDocuments() = ResponseEntity.ok(legalService.getAllDocuments())

    @PostMapping("/legal")
    fun createLegalDocument(@Valid @RequestBody request: CreateLegalDocumentRequest) =
        ResponseEntity.ok(legalService.uploadDocument(request))

    @PostMapping("/legal/{id}/activate")
    fun activateLegalDocument(@PathVariable id: UUID) =
        ResponseEntity.ok(legalService.activateDocument(id))

    @PostMapping("/legal/{id}/deactivate")
    fun deactivateLegalDocument(@PathVariable id: UUID) =
        ResponseEntity.ok(legalService.deactivateDocument(id))
}
