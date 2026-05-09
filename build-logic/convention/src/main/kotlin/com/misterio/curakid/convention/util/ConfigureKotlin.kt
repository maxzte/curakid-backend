package com.misterio.curakid.convention.util

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal fun Project.configureJavaAndKotlin() {
    extensions.configure<JavaPluginExtension> {
        toolchain.apply {
            languageVersion.set(JavaLanguageVersion.of(21))
        }

    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.apply {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        }
    }
}