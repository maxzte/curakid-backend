plugins {
    alias(libs.plugins.curakid.spring.app)
}

group = "com.misterio.curakid"
version = "0.0.1-SNAPSHOT"
description = "curakid"

dependencies {
    implementation(projects.essential)
    implementation(projects.feature.profile)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.cache)
    
    implementation(libs.spring.ai.core)
    implementation(libs.spring.ai.vertex.ai.gemini)

    implementation(libs.postgresql)
    implementation(libs.hibernate.vector)
    
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    
    implementation(libs.google.api.client)
    implementation(libs.poi.ooxml)

    implementation(libs.kotlin.reflect)
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
