plugins {
    kotlin("jvm") version libs.versions.kotlin.get()
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
    `kotlin-dsl`
    alias(libs.plugins.detekt)
    alias(libs.plugins.gradle.publish)
}

val libraryVersion = System.getenv("VERSION") ?: "0.0.1"

group = "io.cookielab.android"
version = libraryVersion

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(libs.bundles.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktxml)
    detektPlugins(libs.detekt.formating.rules)

    testImplementation(libs.bundles.tests)
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

gradlePlugin {
    website = "https://github.com/cookielab/poeditor-gradle-plugin"
    vcsUrl = "https://github.com/cookielab/poeditor-gradle-plugin"
    plugins {
        register("poEditorPlugin") {
            id = "io.cookielab.android.poeditor-gradle-plugin"
            implementationClass = "io.cookielab.android.poeditor.PoEditorPlugin"
            displayName = "PoEditor Gradle plugin"
            description = "Plugin for downloading and verifying PoEditor terms in an Android project."
            tags = setOf("android", "localization", "translations", "strings", "resources", "poeditor")
        }
    }
}

detekt {
    source.setFrom("src")
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    toolVersion = libs.versions.detekt.get()
    parallel = true
}
