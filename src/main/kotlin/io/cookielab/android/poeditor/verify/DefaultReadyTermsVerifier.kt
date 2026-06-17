package io.cookielab.android.poeditor.verify

import io.cookielab.android.poeditor.common.LocalTermsCollector
import io.cookielab.android.poeditor.common.LocaleResolver
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.xml.StringLikeResource
import java.io.File

internal class DefaultReadyTermsVerifier(
    private val localTermsCollector: LocalTermsCollector,
) : ReadyTermsVerifier {

    override fun verify(
        subprojects: List<SubprojectInfo>,
        downloadedDefaultTerms: List<StringLikeResource>,
    ): Map<String, List<String>> {
        val downloadedNames = downloadedDefaultTerms.mapTo(mutableSetOf()) { it.name }
        return subprojects.mapNotNull { subproject ->
            val pending = collectLocalTermNames(subproject).filter { it !in downloadedNames }
            if (pending.isEmpty()) null else subproject.path to pending
        }.toMap()
    }

    /**
     * Collects the names of all terms declared in the subproject's default-locale `values` directories.
     */
    private fun collectLocalTermNames(subproject: SubprojectInfo): Set<String> {
        return subproject.resourceDirs.flatMapTo(mutableSetOf()) { resDir ->
            localTermsCollector.collect(File(resDir, "values${LocaleResolver.DEFAULT_LOCALE_QUALIFIER}"))
                .map { it.name }
        }
    }
}
