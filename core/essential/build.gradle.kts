plugins {
    alias(libs.plugins.curakid.kotlin.library)
}

group = "com.misterio.curakid.core.essential"
version = "unspecified"

dependencies {
    implementation(libs.slf4j)
    testImplementation(kotlin("test"))
}
