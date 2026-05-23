pluginManagement {
    includeBuild("build-logic")

    repositories {
        maven { url = uri("https://repo.spring.io/snapshot") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "curakid"

include("springapp")

// Core modules
include("core:essential")
include("core:security")
include("core:database")
include("core:medical")

// Feature modules
include("feature:auth")
include("feature:children")
include("feature:development")
include("feature:measurements")
include("feature:knowledge")
include("feature:reports")
include("feature:fis")
include("feature:event")
include("feature:assessments")
include("feature:chat")
