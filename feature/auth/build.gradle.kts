plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.auth"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.security)
    implementation(projects.core.database)

    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.thymeleaf)
    implementation(libs.spring.boot.starter.data.redis)

    implementation(libs.google.api.client)
}
