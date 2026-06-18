package com.misterio.curakid.convention

import com.misterio.curakid.convention.util.configureJavaAndKotlin
import com.misterio.curakid.convention.util.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.Actions.with
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension

class FeatureConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
            apply(plugin = "org.jetbrains.kotlin.plugin.spring")
            apply(plugin = "org.springframework.boot")
            apply(plugin = "io.spring.dependency-management")
            apply(plugin = "org.jetbrains.kotlin.plugin.jpa")

            configureJavaAndKotlin()

            val mockitoAgent = configurations.create("mockitoAgent")
            val mockitoAgentFiles = objects.fileCollection().from(mockitoAgent)

            extensions.configure<AllOpenExtension> {
                annotation("jakarta.persistence.Entity")
                annotation("jakarta.persistence.MappedSuperclass")
                annotation("jakarta.persistence.Embeddable")
            }

            dependencies {
                "implementation"(libs.findLibrary("spring.boot.starter.web").get())

                // Security
                "implementation"(libs.findLibrary("spring.boot.starter.security").get())

                // Hibernate + Postgres
                "implementation"(libs.findLibrary("spring.boot.starter.data.jpa").get())
                "runtimeOnly"(libs.findLibrary("postgresql").get())

                // test
                "implementation"(libs.findLibrary("kotlin-reflect").get())
                "testImplementation"(libs.findLibrary("spring.boot.starter.test").get())
                "testImplementation"(libs.findLibrary("spring.boot.starter.webmvc.test").get())
                "testImplementation"(libs.findLibrary("spring.security.test").get())
                "testRuntimeOnly"(libs.findLibrary("h2").get())
                "testImplementation"(libs.findLibrary("mockito.core").get())
                "testImplementation"(libs.findLibrary("mockito-kotlin").get())
                "mockitoAgent"(libs.findLibrary("mockito.core").get()) { isTransitive = false }
            }

            tasks.withType<Test> {
                useJUnitPlatform()
//                doFirst {
//                    jvmArgs("-javaagent:${mockitoAgent.singleFile.absolutePath}")
//                }
                jvmArgumentProviders.add(CommandLineArgumentProvider {
                    listOf("-javaagent:${mockitoAgentFiles.singleFile.absolutePath}")
                })
            }
        }
    }

}
