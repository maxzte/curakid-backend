package com.misterio.curakid.feature.auth.service

import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: SpringTemplateEngine,
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    @Async
    open fun sendVerificationEmail(to: String, name: String, code: String) {
        val context = Context().apply {
            setVariable("name", name)
            setVariable("code", code)
        }
        val html = templateEngine.process("email-verification", context)
        sendHtml(to, "Підтвердження реєстрації - CuraKid", html)
    }

    @Async
    open fun sendPasswordResetEmail(to: String, name: String, code: String) {
        val context = Context().apply {
            setVariable("name", name)
            setVariable("code", code)
        }
        val html = templateEngine.process("password-reset", context)
        sendHtml(to, "Відновлення паролю - CuraKid", html)
    }

    private fun sendHtml(to: String, subject: String, html: String) {
        if (to.endsWith("@gatling.local")) {
            logger.info("Mocking email to $to: Subject: $subject")
            return
        }

        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setTo(to)
            setSubject(subject)
            setText(html, true)
        }
        mailSender.send(message)
    }
}
