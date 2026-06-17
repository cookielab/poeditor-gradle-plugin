package io.cookielab.android.poeditor.verify

import io.cookielab.android.poeditor.common.DefaultLocalTermsCollector
import io.cookielab.android.poeditor.common.DefaultLocaleResolver
import io.cookielab.android.poeditor.common.PoEditorTermsDownloader
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.xml.DefaultStringResParser
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

@UntrackedTask(
    because = "Downloads from external POEditor API to verify local strings; writes nothing and is never up to date",
)
internal abstract class VerifyPoEditorStringsTask : DefaultTask() {

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
    internal abstract val subprojects: ListProperty<SubprojectInfo>

    @TaskAction
    fun process() {
        val subs = subprojects.get()
        logger.lifecycle(
            "verifyPoEditorStrings: verifying {} subproject(s): {}",
            subs.size,
            subs.joinToString { it.path },
        )

        val delegate = createDelegate()
        delegate.execute(subprojects = subs)
        logger.lifecycle(
            "verifyPoEditorStrings: PASS — all local strings across {} subproject(s) are present in POEditor.",
            subs.size,
        )
    }

    /**
     * Wires the concrete collaborators into the delegate, keeping the [process] `@TaskAction` thin. Tests construct
     * [VerifyPoEditorStringsTaskDelegate] directly with fakes — the delegate's constructor is the test seam.
     */
    internal fun createDelegate(): VerifyPoEditorStringsTaskDelegate {
        val resourcesParser = DefaultStringResParser()
        val localeResolver = DefaultLocaleResolver()

        /* Only the default language is needed: the comparison is default-locale only. */
        val defaultLanguage = localeResolver.resolveDefaultLanguage(qualifiersToLanguages.get())
        val termsDownloader = PoEditorTermsDownloader(
            resourcesParser = resourcesParser,
            logger = logger,
            excludedSuffices = excludedSuffices.get(),
            projectId = projectId.get(),
            token = token.get(),
            languages = setOf(defaultLanguage),
        )

        val localTermsCollector = DefaultLocalTermsCollector(
            resourcesParser = resourcesParser,
            readyFileName = readyFileName.get(),
            translatedFileName = translatedFileName.get(),
        )
        val verifier = DefaultReadyTermsVerifier(
            localTermsCollector = localTermsCollector,
        )

        return VerifyPoEditorStringsTaskDelegate(
            termsDownloader = termsDownloader,
            verifier = verifier,
            defaultLanguage = defaultLanguage,
        )
    }
}
