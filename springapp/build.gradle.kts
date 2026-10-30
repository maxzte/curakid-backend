plugins {
    alias(libs.plugins.curakid.spring.app)
    alias(libs.plugins.gatling.plugin)
    alias(libs.plugins.sentry.plugin)
}

group = "com.misterio.curakid"
version = "0.0.1"
description = "curakid"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.security)
    implementation(projects.core.database)
    implementation(projects.core.medical)

    implementation(projects.feature.auth)
    implementation(projects.feature.children)
    implementation(projects.feature.development)
    implementation(projects.feature.measurements)
    implementation(projects.feature.knowledge)
    implementation(projects.feature.reports)
    implementation(projects.feature.fis)
    implementation(projects.feature.event)
    implementation(projects.feature.assessments)
    implementation(projects.feature.chat)
    implementation(projects.feature.notification)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.spring.ai.google.gemini)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.kafka)

    implementation(libs.pdfbox)
    //implementation(libs.sentry.spring.boot.starter)
    implementation(libs.jsoup)

    implementation(libs.kotlin.reflect)
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.security.test)

    testImplementation(libs.google.api.client)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
}
