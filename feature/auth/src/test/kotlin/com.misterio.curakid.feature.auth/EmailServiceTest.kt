package com.misterio.curakid.feature.auth

import com.misterio.curakid.feature.auth.service.EmailService
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.javamail.JavaMailSender
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

class EmailServiceTest {

    private lateinit var mailSender: JavaMailSender
    private lateinit var templateEngine: SpringTemplateEngine
    private lateinit var emailService: EmailService

    @BeforeEach
    fun setUp() {
        mailSender = mock()
        templateEngine = mock()
        emailService = EmailService(mailSender, templateEngine)

        whenever(templateEngine.process(any<String>(), any<Context>())).thenReturn("<html>content</html>")
        whenever(mailSender.createMimeMessage()).thenReturn(mock())
    }

    @Test
    fun `sendVerificationEmail should process email template`() {
        emailService.sendVerificationEmail("parent@test.com", "Parent", "ABC123")

        verify(templateEngine).process(eq("email-verification"), any<Context>())
        verify(mailSender).send(any<MimeMessage>())
    }

    @Test
    fun `sendPasswordResetEmail should process password reset template`() {
        emailService.sendPasswordResetEmail("parent@test.com", "Parent", "XYZ789")

        verify(templateEngine).process(eq("password-reset"), any<Context>())
        verify(mailSender).send(any<MimeMessage>())
    }

    @Test
    fun `should skip sending when email ends with gatling local`() {
        emailService.sendVerificationEmail("test@gatling.local", "Test", "CODE")

        verify(templateEngine).process(any<String>(), any<Context>())
        verify(mailSender, times(0)).send(any<MimeMessage>())
    }

    @Test
    fun `should propagate SMTP exception`() {
        whenever(mailSender.send(any<MimeMessage>())).thenThrow(RuntimeException("SMTP unavailable"))

        Assertions.assertThrows(RuntimeException::class.java) {
            emailService.sendVerificationEmail("parent@test.com", "Parent", "CODE")
        }
    }
}
