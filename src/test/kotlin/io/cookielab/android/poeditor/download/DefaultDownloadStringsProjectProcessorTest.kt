package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.DefaultLocalTermsCollector
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.xml.DefaultStringResParser
import io.cookielab.android.poeditor.xml.FileStringResWriter
import io.cookielab.android.poeditor.xml.StringLikeResource
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers how the processor writes translated.xml/ready.xml across subprojects and languages,
 * exercising the real parser and writer against a temporary directory. Term-resolution
 * behaviour (deduplication, missing terms) lives in [DefaultDownloadStringsProjectProcessorTermsTest].
 */
internal class DefaultDownloadStringsProjectProcessorTest {

    @MockK
    private lateinit var logger: Logger

    @TempDir
    lateinit var tempDir: File

    private val parser = DefaultStringResParser()

    @BeforeTest
    fun initMocks() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    private fun createProcessor(printSyncDate: Boolean = true) = DefaultDownloadStringsProjectProcessor(
        localTermsCollector = DefaultLocalTermsCollector(
            resourcesParser = parser,
            readyFileName = "ready.xml",
            translatedFileName = "translated.xml",
        ),
        resourcesWriter = FileStringResWriter(indent = "    "),
        readyFileName = "ready.xml",
        translatedFileName = "translated.xml",
        printSyncDate = printSyncDate,
        logger = logger,
    )

    private fun resDir(name: String): File = File(tempDir, name).apply { mkdirs() }

    private fun names(file: File): Set<String> = parser.parseFile(file).map { it.name }.toSet()

    @Test
    fun `processor processes single subproject with single language successfully`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "app_name" to "My App", "welcome" to "Welcome")

        createProcessor().processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en"),
            downloadedTerms = mapOf(
                "en" to listOf(
                    StringLikeResource.StringRes("app_name", "My App"),
                    StringLikeResource.StringRes("welcome", "Welcome"),
                )
            ),
            defaultLanguage = "en",
        )

        // translated.xml holds both terms and carries the import date comment
        assertEquals(setOf("app_name", "welcome"), names(translatedFile(res)))
        assertTrue(translatedFile(res).readText().contains("Imported from POEditor"))

        // ready.xml is rewritten empty (every term was present in POEditor) and has no date comment
        assertTrue(names(readyFile(res)).isEmpty())
        assertFalse(readyFile(res).readText().contains("Imported from POEditor"))
    }

    @Test
    fun `processor processes multiple languages for single subproject`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "app_name" to "My App", "welcome" to "Welcome")

        createProcessor().processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en", "-cs" to "cs", "-de" to "de"),
            downloadedTerms = mapOf(
                "en" to listOf(
                    StringLikeResource.StringRes("app_name", "My App"),
                    StringLikeResource.StringRes("welcome", "Welcome"),
                ),
                "cs" to listOf(
                    StringLikeResource.StringRes("app_name", "Moje aplikace"),
                    StringLikeResource.StringRes("welcome", "Vítejte"),
                ),
                "de" to listOf(
                    StringLikeResource.StringRes("app_name", "Meine App"),
                    StringLikeResource.StringRes("welcome", "Willkommen"),
                ),
            ),
            defaultLanguage = "en",
        )

        // A translated.xml is produced for every language directory
        assertEquals(setOf("app_name", "welcome"), names(translatedFile(res, "")))
        assertEquals(setOf("app_name", "welcome"), names(translatedFile(res, "-cs")))
        assertEquals(setOf("app_name", "welcome"), names(translatedFile(res, "-de")))
    }

    @Test
    fun `processor processes multiple subprojects independently`() {
        val app = resDir("app")
        val lib = resDir("lib")
        writeValues(app, "", "ready.xml", "app_name" to "My App")
        writeValues(lib, "", "ready.xml", "lib_name" to "My Lib")

        createProcessor().processSubprojects(
            subprojects = listOf(
                SubprojectInfo(":app", setOf(app.absolutePath)),
                SubprojectInfo(":lib", setOf(lib.absolutePath)),
            ),
            qualifiersToLanguages = mapOf("" to "en"),
            downloadedTerms = mapOf(
                "en" to listOf(
                    StringLikeResource.StringRes("app_name", "My App"),
                    StringLikeResource.StringRes("lib_name", "My Lib"),
                )
            ),
            defaultLanguage = "en",
        )

        assertEquals(setOf("app_name"), names(translatedFile(app)))
        assertEquals(setOf("lib_name"), names(translatedFile(lib)))
    }

    @Test
    fun `processor writes translated xml with the import date comment when printSyncDate is true`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")

        createProcessor(printSyncDate = true).processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en"),
            downloadedTerms = mapOf("en" to listOf(StringLikeResource.StringRes("test", "value"))),
            defaultLanguage = "en",
        )

        assertTrue(translatedFile(res).readText().contains("Imported from POEditor"))
    }

    @Test
    fun `processor writes translated xml without the import date comment when printSyncDate is false`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")

        createProcessor(printSyncDate = false).processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en"),
            downloadedTerms = mapOf("en" to listOf(StringLikeResource.StringRes("test", "value"))),
            defaultLanguage = "en",
        )

        assertFalse(translatedFile(res).readText().contains("Imported from POEditor"))
    }

    @Test
    fun `processor writes ready xml without the import date comment`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")

        createProcessor().processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en"),
            downloadedTerms = mapOf("en" to listOf(StringLikeResource.StringRes("test", "value"))),
            defaultLanguage = "en",
        )

        assertFalse(readyFile(res).readText().contains("Imported from POEditor"))
    }

    @Test
    fun `processor writes a translated xml per language directory and one ready xml`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")

        createProcessor().processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en", "-cs" to "cs", "-de" to "de"),
            downloadedTerms = mapOf(
                "en" to listOf(StringLikeResource.StringRes("test", "value")),
                "cs" to listOf(StringLikeResource.StringRes("test", "hodnota")),
                "de" to listOf(StringLikeResource.StringRes("test", "wert")),
            ),
            defaultLanguage = "en",
        )

        assertTrue(translatedFile(res, "").exists())
        assertTrue(translatedFile(res, "-cs").exists())
        assertTrue(translatedFile(res, "-de").exists())
        // ready.xml only ever lives in the default values directory
        assertTrue(readyFile(res, "").exists())
        assertFalse(readyFile(res, "-cs").exists())
        assertFalse(readyFile(res, "-de").exists())
    }

    @Test
    fun `processor writes nothing for an empty subprojects list`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")

        createProcessor().processSubprojects(
            subprojects = emptyList(),
            qualifiersToLanguages = mapOf("" to "en"),
            downloadedTerms = mapOf("en" to emptyList()),
            defaultLanguage = "en",
        )

        assertFalse(translatedFile(res).exists())
    }

    @Test
    fun `processor skips a language missing from downloaded terms`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")

        createProcessor().processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en", "-cs" to "cs"),
            downloadedTerms = mapOf("en" to listOf(StringLikeResource.StringRes("test", "value"))),
            // "cs" is missing from downloadedTerms
            defaultLanguage = "en",
        )

        assertTrue(translatedFile(res, "").exists())
        assertFalse(translatedFile(res, "-cs").exists())

        // The missing 'cs' language produces exactly one warning naming the language.
        val warning = slot<String>()
        verify(exactly = 1) { logger.warn(capture(warning)) }
        assertTrue(warning.captured.contains("cs"))
    }

    @Test
    fun `processor creates a missing values directory for a translated language`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")
        // The values-cs directory deliberately does not exist yet.
        assertFalse(File(res, "values-cs").exists())

        createProcessor().processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en", "-cs" to "cs"),
            downloadedTerms = mapOf(
                "en" to listOf(StringLikeResource.StringRes("test", "value")),
                "cs" to listOf(StringLikeResource.StringRes("test", "hodnota")),
            ),
            defaultLanguage = "en",
        )

        assertTrue(File(res, "values-cs").isDirectory)
        assertEquals(setOf("test"), names(translatedFile(res, "-cs")))
    }

    @Test
    fun `processor throws when no default values directory is configured`() {
        val res = resDir("app")
        writeValues(res, "-cs", "ready.xml", "test" to "value")

        assertFailsWith<IllegalStateException> {
            createProcessor().processSubprojects(
                subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
                qualifiersToLanguages = mapOf("-cs" to "cs"), // no "" default qualifier
                downloadedTerms = mapOf("cs" to listOf(StringLikeResource.StringRes("test", "hodnota"))),
                defaultLanguage = "cs",
            )
        }
    }

    @Test
    fun `processor throws when the default language is missing from downloaded terms`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "test" to "value")

        // requireNonEmptyTerms throws GradleException when the export has no entry for the default language.
        assertFailsWith<GradleException> {
            createProcessor().processSubprojects(
                subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
                qualifiersToLanguages = mapOf("" to "en"),
                downloadedTerms = emptyMap(), // "en" absent
                defaultLanguage = "en",
            )
        }
    }

    @Test
    fun `processor refuses to overwrite translations when the default language download is empty`() {
        val res = resDir("app")
        writeValues(res, "", "ready.xml", "term_a" to "Value A")
        // A previously-synced translated.xml with real content that must not be wiped.
        writeValues(res, "", "translated.xml", "term_a" to "Hodnota A")

        assertFailsWith<GradleException> {
            createProcessor().processSubprojects(
                subprojects = listOf(SubprojectInfo(":app", setOf(res.absolutePath))),
                qualifiersToLanguages = mapOf("" to "en"),
                downloadedTerms = mapOf("en" to emptyList()), // empty download (e.g. bad token / flaky network)
                defaultLanguage = "en",
            )
        }

        // The existing translated.xml must still hold the original translation.
        assertEquals(setOf("term_a"), names(translatedFile(res)))
    }
}
