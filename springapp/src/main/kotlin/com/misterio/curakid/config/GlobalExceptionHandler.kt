package com.misterio.curakid.config

import com.misterio.curakid.core.essential.common.exceptions.OnboardingRequiredException
import com.misterio.curakid.feature.auth.ConsentRequiredException
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyActivatedException
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyExistException
import com.misterio.curakid.feature.auth.LegalDocumentNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.NoSuchElementException

data class ErrorResponse(
    val message: String,
    val status: Int,
    val errors: Map<String, String>? = null,
    val timestamp: Instant = Instant.now()
)

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(OnboardingRequiredException::class)
    fun handleOnboardingRequired(ex: OnboardingRequiredException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Onboarding required", 412),
            HttpStatus.PRECONDITION_FAILED
        )

    @ExceptionHandler(ConsentRequiredException::class)
    fun handleConsentRequired(ex: ConsentRequiredException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Необхідно надати згоду", 400),
            HttpStatus.BAD_REQUEST
        )

    @ExceptionHandler(LegalDocumentNotFoundException::class)
    fun handleLegalDocumentNotFound(ex: LegalDocumentNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Юридичний документ не знайдено", 404),
            HttpStatus.NOT_FOUND
        )

    @ExceptionHandler(LegalDocumentAlreadyExistException::class)
    fun handleLegalDocumentAlreadyExist(ex: LegalDocumentAlreadyExistException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Юридичний документ вже існує", 409),
            HttpStatus.CONFLICT
        )

    @ExceptionHandler(LegalDocumentAlreadyActivatedException::class)
    fun handleLegalDocumentAlreadyActivated(ex: LegalDocumentAlreadyActivatedException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Юридичний документ вже має такий статус", 409),
            HttpStatus.CONFLICT
        )

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        val message = when {
            ex.message?.contains("Child", ignoreCase = true) == true ->
                "Профіль дитини не знайдено. Будь ласка, переконайтеся, що ви пройшли онбординг та створили профіль дитини."
            else -> ex.message ?: "Об'єкт не знайдено"
        }
        return ResponseEntity(ErrorResponse(message, 404), HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate {
            it.field to (it.defaultMessage ?: "Невірне значення")
        }
        return ResponseEntity(
            ErrorResponse(
                message = "Помилка валідації вхідних даних",
                status = 400,
                errors = fieldErrors
            ),
            HttpStatus.BAD_REQUEST
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse("Некоректний формат JSON або невірний тип даних", 400),
            HttpStatus.BAD_REQUEST
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Невірний запит", 400),
            HttpStatus.BAD_REQUEST
        )

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Помилка стану операції", 400),
            HttpStatus.BAD_REQUEST
        )

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.reason ?: "Помилка запиту", ex.statusCode.value()),
            ex.statusCode
        )

    @ExceptionHandler(AccessDeniedException::class, kotlin.io.AccessDeniedException::class)
    fun handleAccessDenied(ex: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Доступ заборонено", 403),
            HttpStatus.FORBIDDEN
        )

    @ExceptionHandler(org.springframework.security.core.AuthenticationException::class)
    fun handleAuthenticationException(ex: org.springframework.security.core.AuthenticationException): ResponseEntity<ErrorResponse> =
        ResponseEntity(
            ErrorResponse(ex.message ?: "Невірні облікові дані", 401),
            HttpStatus.UNAUTHORIZED
        )

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled internal server error: {}", ex.message, ex)
        io.sentry.Sentry.captureException(ex)
        return ResponseEntity(
            ErrorResponse("Внутрішня помилка сервера. Будь ласка, зверніться до служби підтримки.", 500),
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }
}
