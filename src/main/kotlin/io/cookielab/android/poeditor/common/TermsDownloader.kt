package io.cookielab.android.poeditor.common

import io.cookielab.android.poeditor.xml.StringLikeResource

/**
 * Interface for downloading terms from POEditor API.
 */
internal interface TermsDownloader {

    /**
     * Downloads terms for configured languages from POEditor API.
     *
     * @return map of downloaded terms, where key is the language and value is the list of [StringLikeResource].
     */
    fun downloadTerms(): Map<String, List<StringLikeResource>>
}
