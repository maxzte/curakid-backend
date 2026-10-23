package com.misterio.curakid.feature.notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.misterio.curakid.core.essential.logger
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

@Configuration(proxyBeanMethods = false)
@Lazy(false)
class FirebaseConfig(
    @Value("\${firebase.credentials-path:}")
    private val credentialsPath: String
) {

    private val log = logger()

    @PostConstruct
    fun initializeFirebase() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            log.info("FirebaseApp is already initialized")
            return
        }

        if (credentialsPath.isBlank()) {
            log.warn("Firebase credentials path is not set (firebase.credentials-path is empty). Push notifications will operate in inactive/log-only mode.")
            return
        }

        val credentialsFile = File(credentialsPath)
        if (!credentialsFile.exists()) {
            log.warn("Firebase credentials file does not exist at path: {}. Push notifications will operate in inactive/log-only mode.", credentialsPath)
            return
        }

        try {
            val serviceAccount: InputStream = FileInputStream(credentialsFile)
            serviceAccount.use { stream ->
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build()

                FirebaseApp.initializeApp(options)
                log.info("FirebaseApp initialized successfully with credentials from {}", credentialsPath)
            }
        } catch (e: Exception) {
            log.error("Failed to initialize FirebaseApp from {}: {}", credentialsPath, e.message, e)
        }
    }
}
