plugins {
    alias(libs.plugins.curakid.spring.app)
}

group = "com.misterio.curakid"
version = "0.0.1-SNAPSHOT"
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

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)

    implementation(libs.spring.ai.google.gemini)
    testImplementation(libs.jjwt.api)

    implementation(libs.kotlin.reflect)
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.security.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")

}
