package com.misterio.curakid.security

import com.misterio.curakid.config.GlobalExceptionHandler
import com.misterio.curakid.feature.auth.ConsentRequiredException
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyActivatedException
import com.misterio.curakid.feature.auth.LegalDocumentAlreadyExistException
import com.misterio.curakid.feature.auth.LegalDocumentNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import java.util.NoSuchElementException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `handleNotFound should return custom message for Child not found`() {
        val ex = NoSuchElementException("Child with id 123 not found")
        val response = handler.handleNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Профіль дитини не знайдено. Будь ласка, переконайтеся, що ви пройшли онбординг та створили профіль дитини.", response.body?.message)
    }

    @Test
    fun `handleNotFound should return default message for other elements`() {
        val ex = NoSuchElementException("User not found")
        val response = handler.handleNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("User not found", response.body?.message)
    }

    @Test
    fun `handleOnboardingRequired should return 412 status`() {
        val ex = com.misterio.curakid.core.essential.common.exceptions.OnboardingRequiredException("Custom msg")
        val response = handler.handleOnboardingRequired(ex)

        assertEquals(HttpStatus.PRECONDITION_FAILED, response.statusCode)
        assertEquals("Custom msg", response.body?.message)
    }

    @Test
    fun `handleConsentRequired should return 400 status`() {
        val ex = ConsentRequiredException("Необхідно прийняти умови")
        val response = handler.handleConsentRequired(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Необхідно прийняти умови", response.body?.message)
    }

    @Test
    fun `handleLegalDocumentNotFound should return 404 status`() {
        val ex = LegalDocumentNotFoundException("Документ не знайдено")
        val response = handler.handleLegalDocumentNotFound(ex)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("Документ не знайдено", response.body?.message)
    }

    @Test
    fun `handleLegalDocumentAlreadyExist should return 409 status`() {
        val ex = LegalDocumentAlreadyExistException("Документ вже існує")
        val response = handler.handleLegalDocumentAlreadyExist(ex)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Документ вже існує", response.body?.message)
    }

    @Test
    fun `handleLegalDocumentAlreadyActivated should return 409 status`() {
        val ex = LegalDocumentAlreadyActivatedException("Документ вже активний")
        val response = handler.handleLegalDocumentAlreadyActivated(ex)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("Документ вже активний", response.body?.message)
    }

    @Test
    fun `handleAccessDenied should return 403 for Spring Security AccessDeniedException`() {
        val ex = AccessDeniedException("Access denied")
        val response = handler.handleAccessDenied(ex)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("Access denied", response.body?.message)
    }

    @Test
    fun `handleHttpMessageNotReadable should return 400 with user-friendly message`() {
        val inputMessage = org.springframework.mock.http.MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("JSON parse error", inputMessage)
        val response = handler.handleHttpMessageNotReadable(ex)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Некоректний формат JSON або невірний тип даних", response.body?.message)
    }

    @Test
    fun `handleGenericException should return 500 status and sanitized message`() {
        val ex = RuntimeException("Sensitive database constraint violation or NPE")
        val response = handler.handleGenericException(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Внутрішня помилка сервера. Будь ласка, зверніться до служби підтримки.", response.body?.message)
    }
}
