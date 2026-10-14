plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.chat"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.security)
    implementation(projects.core.database)
    implementation(projects.core.medical)

    implementation(libs.spring.boot.starter.validation)

    implementation(libs.spring.ai.google.gemini)
}
