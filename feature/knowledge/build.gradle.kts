plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.knowledge"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.database)
}
