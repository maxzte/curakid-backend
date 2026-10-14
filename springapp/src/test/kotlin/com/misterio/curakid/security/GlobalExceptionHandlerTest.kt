package com.misterio.curakid.security

import com.misterio.curakid.config.GlobalExceptionHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
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
    fun `handleGenericException should return 500 status and sanitized message`() {
        val ex = RuntimeException("Sensitive database constraint violation or NPE")
        val response = handler.handleGenericException(ex)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Внутрішня помилка сервера. Будь ласка, зверніться до служби підтримки.", response.body?.message)
    }
}
