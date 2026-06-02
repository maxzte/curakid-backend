plugins {
    alias(libs.plugins.curakid.kotlin.library)
}

group = "com.misterio.curakid.core.database"

dependencies {
    implementation(projects.core.essential)

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.json)

    implementation(libs.postgresql)
    implementation(libs.hibernate.vector)
}
