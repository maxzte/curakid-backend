plugins {
    alias(libs.plugins.curakid.kotlin.library)
}

group = "com.misterio.curakid.core.medical"

dependencies {
    implementation(projects.core.database)
    implementation(libs.poi.ooxml)
    implementation(libs.slf4j)
    implementation(libs.spring.boot.starter.web)
}
