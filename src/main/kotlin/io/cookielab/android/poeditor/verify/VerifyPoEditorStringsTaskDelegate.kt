package io.cookielab.android.poeditor.verify

import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.common.TermsDownloader
import io.cookielab.android.poeditor.extensions.requireNonEmptyTerms
import org.gradle.api.GradleException

/**
 * Delegate that contains the business logic for verifying that local string resources are present in POEditor.
 *
 * This delegate is separate from the Gradle task to make the logic testable without Gradle infrastructure. It
 * writes nothing: it downloads the default language, compares it against the local terms and fails when any local
 * term is missing from POEditor.
 *
 * The default language is resolved once by [VerifyPoEditorStringsTask] (which needs it to scope the download to a
 * single language) and passed in, so it is never resolved twice per run.
 */
internal class VerifyPoEditorStringsTaskDelegate(
    private val termsDownloader: TermsDownloader,
    private val verifier: ReadyTermsVerifier,
    private val defaultLanguage: String,
) {

    /**
     * Runs the verification.
     *
     * @param subprojects list of subprojects to verify
     */
    fun execute(subprojects: List<SubprojectInfo>) {
        val defaultTerms = termsDownloader.downloadTerms().requireNonEmptyTerms(defaultLanguage) {
            "POEditor returned no terms for the default language '$defaultLanguage'. " +
                "Refusing to verify against an empty/failed export."
        }

        val pendingTermsBySubproject = verifier.verify(subprojects, defaultTerms)
        if (pendingTermsBySubproject.isNotEmpty()) {
            throw GradleException(pendingTermsMessage(pendingTermsBySubproject))
        }
    }

    private fun pendingTermsMessage(pendingTermsBySubproject: Map<String, List<String>>): String {
        val details = pendingTermsBySubproject.entries.joinToString(separator = "\n") { (path, terms) ->
            "  $path: ${terms.joinToString(separator = ", ")}"
        }
        return "Some local strings are missing from POEditor. " +
            "Upload them to POEditor (or remove them locally) before building:\n$details"
    }
}
