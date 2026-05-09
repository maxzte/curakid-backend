package com.misterio.curakid.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin/ui")
class AdminUiController {

    @GetMapping("", "/")
    fun index(): String {
        return "redirect:/admin/ui/articles"
    }

    @GetMapping("/login")
    fun loginPage(): String {
        return "admin/login"
    }

    @GetMapping("/articles")
    fun articlesPage(): String {
        return "admin/articles"
    }

    @GetMapping("/quizzes")
    fun quizzesPage(): String {
        return "admin/quizzes"
    }

    @GetMapping("/periods")
    fun periodsPage(): String {
        return "admin/periods"
    }

    @GetMapping("/rules")
    fun rulesPage(): String {
        return "admin/rules"
    }

    @GetMapping("/users")
    fun usersPage(): String {
        return "admin/users"
    }

    @GetMapping("/vaccines")
    fun vaccinesPage(): String {
        return "admin/vaccines"
    }
}
