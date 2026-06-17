package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.LocalTermsCollector
import io.cookielab.android.poeditor.common.LocaleResolver
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.extensions.AnsiColor
import io.cookielab.android.poeditor.extensions.inColor
import io.cookielab.android.poeditor.extensions.requireNonEmptyTerms
import io.cookielab.android.poeditor.xml.StringLikeResource
import io.cookielab.android.poeditor.xml.StringResWriter
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File

internal class DefaultDownloadStringsProjectProcessor(
    private val localTermsCollector: LocalTermsCollector,
    private val resourcesWriter: StringResWriter,
    private val readyFileName: String,
    private val translatedFileName: String,
    private val printSyncDate: Boolean,
    private val logger: Logger,
) : DownloadStringsProjectProcessor {

    override fun processSubprojects(
        subprojects: List<SubprojectInfo>,
        qualifiersToLanguages: Map<String, String>,
        downloadedTerms: Map<String, List<StringLikeResource>>,
        defaultLanguage: String,
    ) {
        subprojects.forEach { subproject ->
            processSubproject(
                subproject = subproject,
                qualifiersToLanguages = qualifiersToLanguages,
                downloadedTerms = downloadedTerms,
                defaultLanguage = defaultLanguage,
            )
        }
    }

    private fun processSubproject(
        subproject: SubprojectInfo,
        qualifiersToLanguages: Map<String, String>,
        downloadedTerms: Map<String, List<StringLikeResource>>,
        defaultLanguage: String,
    ) {
        logger.info("Processing subproject '${subproject.path}'…")
        val resourceDirectories = subproject.resourceDirs
            .flatMap { resDirectory ->
                qualifiersToLanguages.map { (qualifier, language) ->
                    val valuesFile = File(resDirectory, "values$qualifier")
                    if (!valuesFile.exists() && !valuesFile.mkdirs()) {
                        throw GradleException("Could not create resources directory '${valuesFile.absolutePath}'.")
                    }
                    ValuesDirectory(
                        isDefault = qualifier == LocaleResolver.DEFAULT_LOCALE_QUALIFIER,
                        language = language,
                        path = valuesFile.absolutePath,
                    )
                }
            }

        val defaultResourceDirectories = resourceDirectories.filter { it.isDefault }
        check(defaultResourceDirectories.size == 1) {
            val directories = defaultResourceDirectories.joinToString { it.path }
            "Invalid number of default resources directories found: $directories."
        }
        val defaultDirectory = defaultResourceDirectories.first()

        val uniqueTermsToTranslate = localTermsCollector.collect(File(defaultDirectory.path))
        if (uniqueTermsToTranslate.isEmpty()) {
            logger.info("No terms to translate for subproject '${subproject.path}' found.")
            return
        }
        logger.info("Found ${uniqueTermsToTranslate.size} unique terms in '${subproject.path}'.")

        val defaultDownloaded = downloadedTerms.requireNonEmptyTerms(defaultLanguage) {
            "POEditor returned no terms for the default language '$defaultLanguage'. " +
                "Refusing to overwrite existing translations in '${subproject.path}'."
        }

        val uniqueTermNames = uniqueTermsToTranslate.mapTo(mutableSetOf()) { it.name }
        resourceDirectories.forEach { directory ->
            processResourceDirectory(
                directory = directory,
                downloadedTerms = downloadedTerms,
                uniqueTermsToTranslate = uniqueTermNames,
            )
        }

        writeMissingTermsToReady(
            subprojectPath = subproject.path,
            defaultDirectory = defaultDirectory,
            uniqueTermsToTranslate = uniqueTermsToTranslate,
            defaultDownloaded = defaultDownloaded,
        )
    }

    /**
     * Writes the terms missing from POEditor into the default `ready.xml` (cleanup phase) and emits a single
     * subproject-level warning naming them.
     */
    private fun writeMissingTermsToReady(
        subprojectPath: String,
        defaultDirectory: ValuesDirectory,
        uniqueTermsToTranslate: Collection<StringLikeResource>,
        defaultDownloaded: List<StringLikeResource>,
    ) {
        val defaultDownloadedTerms = defaultDownloaded.mapTo(mutableSetOf()) { it.name }
        val termsWithoutTranslations = uniqueTermsToTranslate.filter { it.name !in defaultDownloadedTerms }

        val readyFile = getReadyFile(File(defaultDirectory.path))
        resourcesWriter.createAndWrite(termsWithoutTranslations, readyFile, printDate = false)
        if (termsWithoutTranslations.isNotEmpty()) {
            logger.warn(
                termsWithoutTranslations.joinToString(
                    prefix = "Some strings weren't present in POEditor: [",
                    separator = ", ",
                    postfix = "]. They were written to '${readyFile.absolutePath}'",
                ) { it.name }.inColor(AnsiColor.YELLOW)
            )
        }
        logger.lifecycle(
            "Module {} synced. Of {} unique terms, {} terms without translation in POEditor.",
            subprojectPath,
            uniqueTermsToTranslate.size,
            termsWithoutTranslations.size,
        )
    }

    private fun processResourceDirectory(
        directory: ValuesDirectory,
        downloadedTerms: Map<String, List<StringLikeResource>>,
        uniqueTermsToTranslate: Collection<String>,
    ) {
        val translatedStrings = downloadedTerms[directory.language]
        if (translatedStrings == null) {
            logger.warn("No translations found for language '${directory.language}'.".inColor(AnsiColor.YELLOW))
            return
        }
        val dirFile = File(directory.path)
        val translatedFile = getTranslatedFile(dirFile)
        /* Write translated.xml (createAndWrite creates the file if it doesn't exist yet). */
        val termsToUse = translatedStrings.filter { it.name in uniqueTermsToTranslate }
        resourcesWriter.createAndWrite(termsToUse, translatedFile, printDate = printSyncDate)
        logger.info("Wrote ${termsToUse.size} terms to '${dirFile.name}/${translatedFile.name}'")
    }

    /**
     * Shortcut to create a ready file.
     */
    private fun getReadyFile(directory: File): File {
        return File(directory, readyFileName)
    }

    /**
     * Shortcut to create a translated file.
     */
    private fun getTranslatedFile(directory: File): File {
        return File(directory, translatedFileName)
    }

    private data class ValuesDirectory(
        val isDefault: Boolean,
        val language: String,
        val path: String,
    )
}
