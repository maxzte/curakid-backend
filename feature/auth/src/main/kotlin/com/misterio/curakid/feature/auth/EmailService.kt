package com.misterio.curakid.feature.auth

import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: SpringTemplateEngine,
) {

    fun sendVerificationEmail(to: String, name: String, code: String) {
        val context = Context().apply {
            setVariable("name", name)
            setVariable("code", code)
        }
        val html = templateEngine.process("email-verification", context)
        sendHtml(to, "Підтвердження реєстрації - CuraKid", html)
    }

    fun sendPasswordResetEmail(to: String, name: String, code: String) {
        val context = Context().apply {
            setVariable("name", name)
            setVariable("code", code)
        }
        val html = templateEngine.process("password-reset", context)
        sendHtml(to, "Відновлення паролю - CuraKid", html)
    }

    private fun sendHtml(to: String, subject: String, html: String) {
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setTo(to)
            setSubject(subject)
            setText(html, true)
        }
        mailSender.send(message)
    }
}
