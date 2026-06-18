plugins {
    alias(libs.plugins.curakid.feature)
}

group = "com.misterio.curakid.feature.fis"

dependencies {
    implementation(projects.core.essential)

    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.google.genai.embedding)

    testImplementation(libs.okhttp.mockwebserver)
}
