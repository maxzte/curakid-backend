plugins {
    alias(libs.plugins.curakid.kotlin.library)
    alias(libs.plugins.kotlin.spring)
}

group = "com.misterio.curakid.core.security"

dependencies {
    implementation(projects.core.database)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.json)

    implementation("com.google.guava:guava:33.4.0-jre")

    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
}
