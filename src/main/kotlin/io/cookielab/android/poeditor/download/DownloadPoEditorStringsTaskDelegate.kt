package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.DefaultLocaleResolver
import io.cookielab.android.poeditor.common.LocaleResolver
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.common.TermsDownloader

/**
 * Delegate that contains the business logic for downloading and syncing translations from POEditor.
 *
 * This delegate is separate from the Gradle task to make the logic testable without Gradle infrastructure.
 */
internal class DownloadPoEditorStringsTaskDelegate(
    private val termsDownloader: TermsDownloader,
    private val projectProcessor: DownloadStringsProjectProcessor,
    private val localeResolver: LocaleResolver = DefaultLocaleResolver(),
) {

    /**
     * Executes the full download and sync process.
     *
     * @param subprojects list of subprojects to process
     * @param qualifiersToLanguages mapping of Android resource qualifiers to POEditor languages
     */
    fun execute(
        subprojects: List<SubprojectInfo>,
        qualifiersToLanguages: Map<String, String>,
    ) {
        val defaultLanguage = localeResolver.resolveDefaultLanguage(qualifiersToLanguages)

        val downloadedTerms = termsDownloader.downloadTerms()

        projectProcessor.processSubprojects(
            subprojects = subprojects,
            qualifiersToLanguages = qualifiersToLanguages,
            downloadedTerms = downloadedTerms,
            defaultLanguage = defaultLanguage,
        )
    }
}
