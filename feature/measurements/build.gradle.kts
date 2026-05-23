plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.measurements"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.database)
    implementation(projects.core.medical)

    implementation(libs.spring.boot.starter.validation)
}
