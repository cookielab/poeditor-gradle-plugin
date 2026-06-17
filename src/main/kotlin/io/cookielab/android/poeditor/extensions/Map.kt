package io.cookielab.android.poeditor.extensions

import io.cookielab.android.poeditor.xml.StringLikeResource
import org.gradle.api.GradleException

/**
 * Returns the terms downloaded for [language], guarding against a failed/empty POEditor export.
 *
 * POEditor signals a failed export with an empty body (e.g. an invalid token or a flaky network), which would
 * otherwise make every local term look "missing" — wiping committed translations on the download path and raising a
 * false alarm on the verify path. Both paths therefore refuse to proceed against an empty export. Keeping the check
 * in one place means the download sync and the verify gate cannot drift in what they treat as a valid export.
 *
 * @param language the POEditor language whose terms must be present and non-empty.
 * @param onEmpty builds the user-facing message describing what is being refused (download and verify differ).
 * @return the non-empty terms for [language].
 * @throws GradleException if there is no entry for [language] or that entry is empty.
 */
internal fun Map<String, List<StringLikeResource>>.requireNonEmptyTerms(
    language: String,
    onEmpty: () -> String,
): List<StringLikeResource> {
    val terms = this[language].orEmpty()
    if (terms.isEmpty()) {
        throw GradleException(onEmpty())
    }
    return terms
}
