pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
//        maven { url = uri("https://repo.spring.io/snapshot") }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
//        maven { url = uri("https://repo.spring.io/snapshot") }
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
