import org.gradle.kotlin.dsl.implementation
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val javaVersion = JavaVersion.VERSION_21

plugins {
    `kotlin-dsl`
}

group = "com.misterio.curakid.buildlogic"

java {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.spring.gradlePlugin)
    implementation(libs.spring.boot.gradlePlugin)
//    implementation(libs.spring.dependency.management.gradlePlugin)
    implementation(libs.kotlin.jpa.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("springApp") {
            id = libs.plugins.curakid.spring.app.get().pluginId
            implementationClass = "com.misterio.curakid.convention.SpringAppConventionPlugin"
        }

        register("feature") {
            id = libs.plugins.curakid.feature.get().pluginId
            implementationClass = "com.misterio.curakid.convention.FeatureConventionPlugin"
        }

        register("library") {
            id = libs.plugins.curakid.kotlin.library.get().pluginId
            implementationClass = "com.misterio.curakid.convention.KotlinLibConventionPlugin"
        }
    }
}
