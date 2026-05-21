plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.chat"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.security)
    implementation(projects.core.database)

    implementation(projects.feature.reports) // To reuse Intelligence/Knowledge provider and context extraction if needed
    implementation(projects.feature.children) // Child repository

    implementation(libs.spring.boot.starter.validation)

    implementation(libs.spring.ai.google.gemini)
}
