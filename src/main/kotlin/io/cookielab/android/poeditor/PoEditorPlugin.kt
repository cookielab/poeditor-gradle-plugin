package io.cookielab.android.poeditor

import com.android.build.gradle.BaseExtension
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.download.DownloadPoEditorStringsTask
import io.cookielab.android.poeditor.verify.VerifyPoEditorStringsTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Main class for POEditor sync plugin.
 */
public class PoEditorPlugin : Plugin<Project> {

    /* This set is used to determine which projects might contain android resources. */
    private val androidPlugins = setOf(
        "com.android.application",
        "com.android.dynamic-feature",
        "com.android.library",
    )

    override fun apply(target: Project) {
        val extension = target.extensions.create<PoEditorPluginExtension>("poEditorSync").apply {
            projectId.unsetConvention()
            token.unsetConvention()
            qualifiersToLanguages.unsetConvention()
            readyFileName.convention("ready.xml")
            translatedFileName.convention("translated.xml")
            excludedSuffices.convention(setOf("_ios"))
            indent.convention("    ")
            printSyncDate.convention(false)
        }

        val downloadTask = target.tasks.register<DownloadPoEditorStringsTask>("downloadPoEditorStrings") {
            group = "PoEditor sync"
            description = "Downloads the translated strings from PoEditor"
            projectId.set(extension.projectId)
            token.set(extension.token)
            qualifiersToLanguages.set(extension.qualifiersToLanguages)
            readyFileName.set(extension.readyFileName)
            translatedFileName.set(extension.translatedFileName)
            excludedSuffices.set(extension.excludedSuffices)
            indent.set(extension.indent)
            printSyncDate.set(extension.printSyncDate)
        }

        val verifyTask = target.tasks.register<VerifyPoEditorStringsTask>("verifyPoEditorStrings") {
            group = "PoEditor sync"
            description = "Verifies that all local strings are present in PoEditor; fails the build if any are missing"
            projectId.set(extension.projectId)
            token.set(extension.token)
            qualifiersToLanguages.set(extension.qualifiersToLanguages)
            readyFileName.set(extension.readyFileName)
            translatedFileName.set(extension.translatedFileName)
            excludedSuffices.set(extension.excludedSuffices)
        }

        /*
         * Capture subproject metadata only after every project has been evaluated. Doing this eagerly during
         * apply() races subproject configuration: the subprojects may not have applied their Android plugins yet,
         * so `hasPlugin(...)` returns false and the captured list comes back empty — which makes the verify task
         * silently pass and the download task a no-op. `projectsEvaluated` guarantees the plugins (and their
         * resource source sets) are in place.
         */
        target.gradle.projectsEvaluated {
            val subprojectInfos = captureAndroidSubprojects(target)
            downloadTask.configure { subprojects.set(subprojectInfos) }
            verifyTask.configure { subprojects.set(subprojectInfos) }
        }
    }

    /**
     * Captures the path and resource directories of every Android subproject. Must be called only after all
     * projects have been evaluated, so the subprojects' Android plugins (and their resource source sets) are
     * applied.
     */
    private fun captureAndroidSubprojects(target: Project): List<SubprojectInfo> {
        return target.subprojects
            .filter { subproject -> androidPlugins.any { pluginId -> subproject.plugins.hasPlugin(pluginId) } }
            .map { subproject ->
                SubprojectInfo(
                    path = subproject.path,
                    resourceDirs = subproject.extensions.getByType(BaseExtension::class)
                        .sourceSets.getByName("main")
                        .res
                        .srcDirs
                        .mapTo(mutableSetOf()) { it.absolutePath },
                )
            }
    }
}
