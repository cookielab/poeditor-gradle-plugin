package io.cookielab.android.poeditor.verify

import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.xml.StringLikeResource

/**
 * Interface for verifying that local string resources are present in POEditor, without writing any files.
 */
internal interface ReadyTermsVerifier {

    /**
     * Compares the local default-locale terms of each subproject against [downloadedDefaultTerms] and reports the
     * terms that are missing from POEditor (i.e. the terms that would land in `ready.xml`).
     *
     * @param subprojects list of subprojects to verify
     * @param downloadedDefaultTerms the terms downloaded from POEditor for the default language
     * @return map of subproject path to the names of its pending (missing-from-POEditor) terms; only subprojects
     *   with at least one pending term are included.
     */
    fun verify(
        subprojects: List<SubprojectInfo>,
        downloadedDefaultTerms: List<StringLikeResource>,
    ): Map<String, List<String>>
}
