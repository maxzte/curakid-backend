plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.children"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.database)

    implementation(libs.spring.boot.starter.validation)
}
