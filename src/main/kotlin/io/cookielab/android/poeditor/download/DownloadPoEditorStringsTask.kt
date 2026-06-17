package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.DefaultLocalTermsCollector
import io.cookielab.android.poeditor.common.PoEditorTermsDownloader
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.xml.DefaultStringResParser
import io.cookielab.android.poeditor.xml.FileStringResWriter
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

@UntrackedTask(
    because = "Downloads from external POEditor API and syncs translations across multiple subproject directories",
)
internal abstract class DownloadPoEditorStringsTask : DefaultTask() {

    /* Properties loaded from extension */
    @get:Input
    internal abstract val projectId: Property<String>

    @get:Input
    internal abstract val token: Property<String>

    @get:Input
    internal abstract val qualifiersToLanguages: MapProperty<String, String>

    @get:Input
    internal abstract val readyFileName: Property<String>

    @get:Input
    internal abstract val translatedFileName: Property<String>

    @get:Input
    internal abstract val excludedSuffices: SetProperty<String>

    @get:Input
    internal abstract val indent: Property<String>

    @get:Input
    internal abstract val printSyncDate: Property<Boolean>

    @get:Input
    internal abstract val subprojects: ListProperty<SubprojectInfo>

    @TaskAction
    fun process() {
        val delegate = createDelegate()
        delegate.execute(
            subprojects = subprojects.get(),
            qualifiersToLanguages = qualifiersToLanguages.get(),
        )
    }

    /**
     * Wires the concrete collaborators into the delegate, keeping the [process] `@TaskAction` thin. Tests construct
     * [DownloadPoEditorStringsTaskDelegate] directly with fakes — the delegate's constructor is the test seam.
     */
    internal fun createDelegate(): DownloadPoEditorStringsTaskDelegate {
        val resourcesParser = DefaultStringResParser()
        val resourcesWriter = FileStringResWriter(indent = indent.get())
        val localTermsCollector = DefaultLocalTermsCollector(
            resourcesParser = resourcesParser,
            readyFileName = readyFileName.get(),
            translatedFileName = translatedFileName.get(),
        )

        val termsDownloader = PoEditorTermsDownloader(
            resourcesParser = resourcesParser,
            logger = logger,
            excludedSuffices = excludedSuffices.get(),
            projectId = projectId.get(),
            token = token.get(),
            languages = qualifiersToLanguages.get().values.toSet(),
        )

        val projectProcessor = DefaultDownloadStringsProjectProcessor(
            localTermsCollector = localTermsCollector,
            resourcesWriter = resourcesWriter,
            readyFileName = readyFileName.get(),
            translatedFileName = translatedFileName.get(),
            printSyncDate = printSyncDate.get(),
            logger = logger,
        )

        return DownloadPoEditorStringsTaskDelegate(
            termsDownloader = termsDownloader,
            projectProcessor = projectProcessor,
        )
    }
}
