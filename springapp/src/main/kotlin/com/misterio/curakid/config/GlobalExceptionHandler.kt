package com.misterio.curakid.config

import com.misterio.curakid.core.essential.common.exceptions.OnboardingRequiredException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.NoSuchElementException

data class ErrorResponse(val message: String, val status: Int)

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(OnboardingRequiredException::class)
    fun handleOnboardingRequired(ex: OnboardingRequiredException): ResponseEntity<ErrorResponse> =
        ResponseEntity(ErrorResponse(ex.message ?: "Onboarding required", 412), HttpStatus.PRECONDITION_FAILED)

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        val message = when {
            ex.message?.contains("Child", ignoreCase = true) == true ->
                "Профіль дитини не знайдено. Будь ласка, переконайтеся, що ви пройшли онбординг та створили профіль дитини."

            else -> ex.message ?: "Об'єкт не знайдено"
        }
        return ResponseEntity(ErrorResponse(message, 404), HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity(ErrorResponse(ex.message ?: "Bad request", 400), HttpStatus.BAD_REQUEST)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity(ErrorResponse("Access denied", 403), HttpStatus.FORBIDDEN)
}
