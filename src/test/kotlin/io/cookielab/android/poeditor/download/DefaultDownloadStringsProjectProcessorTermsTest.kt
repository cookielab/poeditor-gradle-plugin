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
import org.gradle.api.logging.Logger
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers how the processor resolves the set of terms to translate: reading existing
 * ready.xml/translated.xml, deduplication, filtering downloaded terms, and detecting
 * terms missing from POEditor. Write/per-language behaviour lives in
 * [DefaultDownloadStringsProjectProcessorTest].
 */
internal class DefaultDownloadStringsProjectProcessorTermsTest {

    @MockK
    private lateinit var logger: Logger

    @TempDir
    lateinit var tempDir: File

    private val parser = DefaultStringResParser()

    @BeforeTest
    fun initMocks() {
        MockKAnnotations.init(this, relaxUnitFun = true)
    }

    private fun createProcessor() = DefaultDownloadStringsProjectProcessor(
        localTermsCollector = DefaultLocalTermsCollector(
            resourcesParser = parser,
            readyFileName = "ready.xml",
            translatedFileName = "translated.xml",
        ),
        resourcesWriter = FileStringResWriter(indent = "    "),
        readyFileName = "ready.xml",
        translatedFileName = "translated.xml",
        printSyncDate = true,
        logger = logger,
    )

    private fun resDir(name: String = "app"): File = File(tempDir, name).apply { mkdirs() }

    private fun names(file: File): Set<String> = parser.parseFile(file).map { it.name }.toSet()

    private fun run(resDir: File, downloadedEn: List<StringLikeResource>) {
        createProcessor().processSubprojects(
            subprojects = listOf(SubprojectInfo(":app", setOf(resDir.absolutePath))),
            qualifiersToLanguages = mapOf("" to "en"),
            downloadedTerms = mapOf("en" to downloadedEn),
            defaultLanguage = "en",
        )
    }

    @Test
    fun `processor collects unique terms from both ready and translated xml files`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A", "term_b" to "Value B")
        writeValues(res, "", "translated.xml", "term_c" to "Value C", "term_d" to "Value D")

        run(
            res,
            downloadedEn = listOf(
                StringLikeResource.StringRes("term_a", "Value A"),
                StringLikeResource.StringRes("term_b", "Value B"),
                StringLikeResource.StringRes("term_c", "Value C"),
                StringLikeResource.StringRes("term_d", "Value D"),
            ),
        )

        assertEquals(setOf("term_a", "term_b", "term_c", "term_d"), names(translatedFile(res)))
    }

    @Test
    fun `processor deduplicates terms by name across ready and translated`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "duplicate_term" to "Value in ready")
        writeValues(res, "", "translated.xml", "duplicate_term" to "Value in translated")

        run(res, downloadedEn = listOf(StringLikeResource.StringRes("duplicate_term", "Value from POEditor")))

        assertEquals(setOf("duplicate_term"), names(translatedFile(res)))
    }

    @Test
    fun `processor writes only the locally known terms, ignoring extra downloaded ones`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "wanted_1" to "Value 1", "wanted_2" to "Value 2")

        run(
            res,
            downloadedEn = listOf(
                StringLikeResource.StringRes("wanted_1", "Value 1"),
                StringLikeResource.StringRes("wanted_2", "Value 2"),
                StringLikeResource.StringRes("unwanted_3", "Value 3"),
                StringLikeResource.StringRes("unwanted_4", "Value 4"),
                StringLikeResource.StringRes("unwanted_5", "Value 5"),
            ),
        )

        assertEquals(setOf("wanted_1", "wanted_2"), names(translatedFile(res)))
    }

    @Test
    fun `processor writes terms missing from POEditor into ready xml`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A", "term_b" to "Value B", "term_c" to "Value C")

        run(res, downloadedEn = listOf(StringLikeResource.StringRes("term_a", "Value A")))

        // term_b and term_c weren't in POEditor, so they land back in ready.xml
        assertEquals(setOf("term_b", "term_c"), names(readyFile(res)))
    }

    @Test
    fun `processor writes an empty ready xml when no terms are missing`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A")

        run(res, downloadedEn = listOf(StringLikeResource.StringRes("term_a", "Value A")))

        assertTrue(names(readyFile(res)).isEmpty())
    }

    @Test
    fun `processor writes nothing when the default values dir has no ready or translated files`() {
        val res = resDir()

        run(res, downloadedEn = emptyList())

        assertFalse(translatedFile(res).exists())
        assertFalse(readyFile(res).exists())
    }

    @Test
    fun `processor syncs a term defined only in ready xml`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "ready_only" to "value")

        run(res, downloadedEn = listOf(StringLikeResource.StringRes("ready_only", "value")))

        assertEquals(setOf("ready_only"), names(translatedFile(res)))
    }

    @Test
    fun `processor syncs a term defined only in translated xml`() {
        val res = resDir()
        writeValues(res, "", "translated.xml", "translated_only" to "value")

        run(res, downloadedEn = listOf(StringLikeResource.StringRes("translated_only", "value")))

        assertEquals(setOf("translated_only"), names(translatedFile(res)))
    }

    @Test
    fun `processor logs a single warning that lists exactly the terms missing from POEditor`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A", "term_b" to "Value B", "term_c" to "Value C")

        run(res, downloadedEn = listOf(StringLikeResource.StringRes("term_a", "Value A")))

        // A single subproject-level warning is emitted, listing exactly the missing terms (term_a is present).
        val warning = slot<String>()
        verify(exactly = 1) { logger.warn(capture(warning)) }
        assertTrue(warning.captured.contains("term_b"))
        assertTrue(warning.captured.contains("term_c"))
        assertFalse(warning.captured.contains("term_a"))
    }

    @Test
    fun `processor does not warn when every term is present in POEditor`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A", "term_b" to "Value B")

        run(
            res,
            downloadedEn = listOf(
                StringLikeResource.StringRes("term_a", "Value A"),
                StringLikeResource.StringRes("term_b", "Value B"),
            ),
        )

        verify(exactly = 0) { logger.warn(any<String>()) }
    }
}
