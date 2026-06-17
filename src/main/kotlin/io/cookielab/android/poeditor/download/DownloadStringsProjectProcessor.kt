package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.xml.StringLikeResource

/**
 * Interface for processing subprojects and syncing translations.
 */
internal interface DownloadStringsProjectProcessor {

    /**
     * Processes all [subprojects] and syncs their translations using [downloadedTerms].
     *
     * @param subprojects list of subprojects to process
     * @param qualifiersToLanguages mapping of Android resource qualifiers to POEditor languages
     * @param downloadedTerms map of downloaded terms from POEditor, keyed by language
     * @param defaultLanguage the POEditor language mapped to the default locale (resolved once by the delegate)
     */
    fun processSubprojects(
        subprojects: List<SubprojectInfo>,
        qualifiersToLanguages: Map<String, String>,
        downloadedTerms: Map<String, List<StringLikeResource>>,
        defaultLanguage: String,
    )
}
