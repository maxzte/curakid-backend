plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.fis"

dependencies {
    implementation(projects.core.essential)

    implementation(libs.spring.boot.starter.webflux)
}
