plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.reports"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.database)
    implementation(projects.core.medical)

    implementation(libs.spring.ai.google.gemini)
}
