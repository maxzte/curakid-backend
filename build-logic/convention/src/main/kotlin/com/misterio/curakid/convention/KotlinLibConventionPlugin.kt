package com.misterio.curakid.convention

import com.misterio.curakid.convention.util.configureJavaAndKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

class KotlinLibConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")

            configureJavaAndKotlin()
        }
    }

}
