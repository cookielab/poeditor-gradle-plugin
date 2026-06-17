package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.DefaultLocalTermsCollector
import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.verify.DefaultReadyTermsVerifier
import io.cookielab.android.poeditor.xml.DefaultStringResParser
import io.cookielab.android.poeditor.xml.StringLikeResource
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DefaultReadyTermsVerifierTest {

    @TempDir
    lateinit var tempDir: File

    private val parser = DefaultStringResParser()

    private fun createVerifier() = DefaultReadyTermsVerifier(
        localTermsCollector = DefaultLocalTermsCollector(
            resourcesParser = parser,
            readyFileName = "ready.xml",
            translatedFileName = "translated.xml",
        ),
    )

    private fun resDir(name: String = "app"): File = File(tempDir, name).apply { mkdirs() }

    private fun subproject(path: String, resDir: File) = SubprojectInfo(path, setOf(resDir.absolutePath))

    private fun string(name: String) = StringLikeResource.StringRes(name, "value")

    @Test
    fun `returns empty map when all local terms are present in POEditor`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A")

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", res)),
            downloadedDefaultTerms = listOf(string("term_a")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `reports terms missing from POEditor`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A", "term_b" to "Value B", "term_c" to "Value C")

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", res)),
            downloadedDefaultTerms = listOf(string("term_a")),
        )

        assertEquals(setOf(":app"), result.keys)
        assertEquals(setOf("term_b", "term_c"), result.getValue(":app").toSet())
    }

    @Test
    fun `collects local terms from both ready and translated xml`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "ready_term" to "Value R")
        writeValues(res, "", "translated.xml", "translated_term" to "Value T")

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", res)),
            downloadedDefaultTerms = listOf(string("other")),
        )

        assertEquals(setOf("ready_term", "translated_term"), result.getValue(":app").toSet())
    }

    @Test
    fun `ignores downloaded terms that do not exist locally`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "term_a" to "Value A")

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", res)),
            downloadedDefaultTerms = listOf(string("term_a"), string("extra_1"), string("extra_2")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `aggregates pending terms per subproject`() {
        val appRes = resDir("app")
        writeValues(appRes, "", "ready.xml", "term_a" to "Value A")
        val libRes = resDir("lib")
        writeValues(libRes, "", "ready.xml", "term_b" to "Value B")

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", appRes), subproject(":lib", libRes)),
            downloadedDefaultTerms = listOf(string("term_a")),
        )

        // Only :lib has a missing term; :app is fully present and absent from the result.
        assertEquals(setOf(":lib"), result.keys)
        assertEquals(setOf("term_b"), result.getValue(":lib").toSet())
    }

    @Test
    fun `excludes subprojects with no local resource files`() {
        val res = resDir()

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", res)),
            downloadedDefaultTerms = listOf(string("term_a")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `aggregates pending terms across multiple failing subprojects`() {
        val appRes = resDir("app")
        writeValues(appRes, "", "ready.xml", "term_x" to "X")
        val libRes = resDir("lib")
        writeValues(libRes, "", "ready.xml", "term_y" to "Y")

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", appRes), subproject(":lib", libRes)),
            downloadedDefaultTerms = emptyList(),
        )

        assertEquals(setOf(":app", ":lib"), result.keys)
        assertEquals(setOf("term_x"), result.getValue(":app").toSet())
        assertEquals(setOf("term_y"), result.getValue(":lib").toSet())
    }

    @Test
    fun `reports a name present in both ready and translated only once`() {
        val res = resDir()
        writeValues(res, "", "ready.xml", "dup" to "from ready")
        writeValues(res, "", "translated.xml", "dup" to "from translated")

        val result = createVerifier().verify(
            subprojects = listOf(subproject(":app", res)),
            downloadedDefaultTerms = emptyList(),
        )

        assertEquals(listOf("dup"), result.getValue(":app"))
    }
}
