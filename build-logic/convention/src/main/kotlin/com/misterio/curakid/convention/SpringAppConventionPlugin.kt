package com.misterio.curakid.convention

import com.misterio.curakid.convention.util.configureJavaAndKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.withType


class SpringAppConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
            apply(plugin = "org.jetbrains.kotlin.plugin.spring")
            apply(plugin = "org.springframework.boot")
            apply(plugin = "io.spring.dependency-management")

            configureJavaAndKotlin()

            tasks.withType<Test> {
                useJUnitPlatform()
            }
        }
    }

}
