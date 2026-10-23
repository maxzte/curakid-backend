plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.notification"

dependencies {
    implementation(projects.core.essential)
    implementation(projects.core.database)

    implementation(libs.firebase.admin)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.redis)
}
