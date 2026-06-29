package io.cookielab.android.poeditor

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoEditorPluginAgp9Test {

    @Test
    fun `configures Android project with AGP 9 new DSL`() {
        val projectDir = createTempDirectory(prefix = "poeditor-agp9-consumer").toFile()
        projectDir.writeSettings()
        projectDir.writeRootBuild()
        projectDir.writeAppBuild()
        projectDir.writeReadyStrings()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("printPoEditorSubprojects", "--stacktrace")
            .build()

        assertTrue(result.output.contains("SubprojectInfo(path=:app"))
        assertTrue(result.output.contains("app/src/main/res"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":printPoEditorSubprojects")?.outcome)
    }

    private fun File.writeSettings() {
        resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "poeditor-agp9-consumer"
            include(":app")
            """.trimIndent(),
        )
    }

    private fun File.writeRootBuild() {
        resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "9.2.1" apply false
                id("io.cookielab.android.poeditor-gradle-plugin")
            }

            poEditorSync {
                projectId = "123"
                token = "token"
                qualifiersToLanguages = mapOf("" to "en")
            }

            tasks.register("printPoEditorSubprojects") {
                doLast {
                    val task = tasks.named("downloadPoEditorStrings").get()
                    val subprojectsGetter = task.javaClass.methods.first {
                        it.name.startsWith("getSubprojects") && it.parameterCount == 0
                    }
                    val subprojectsProperty = subprojectsGetter.invoke(task)
                    val subprojects = subprojectsProperty.javaClass.methods.first {
                        it.name == "get" && it.parameterCount == 0
                    }.invoke(subprojectsProperty)
                    println("poeditor-subprojects=${'$'}subprojects")
                }
            }
            """.trimIndent(),
        )
    }

    private fun File.writeAppBuild() {
        resolve("app").mkdirs()
        resolve("app/build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
            }

            android {
                namespace = "io.cookielab.poeditor.test"
                compileSdk = 37

                defaultConfig {
                    minSdk = 23
                }
            }
            """.trimIndent(),
        )
    }

    private fun File.writeReadyStrings() {
        resolve("app/src/main/res/values").mkdirs()
        resolve("app/src/main/res/values/ready.xml").writeText(
            """
            <resources>
                <string name="hello">Hello</string>
            </resources>
            """.trimIndent(),
        )
    }
}
