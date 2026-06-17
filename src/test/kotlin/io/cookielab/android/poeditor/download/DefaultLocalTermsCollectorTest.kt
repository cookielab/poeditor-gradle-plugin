package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.DefaultLocalTermsCollector
import io.cookielab.android.poeditor.xml.DefaultStringResParser
import io.cookielab.android.poeditor.xml.StringLikeResource
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class DefaultLocalTermsCollectorTest {

    @TempDir
    lateinit var tempDir: File

    private val collector = DefaultLocalTermsCollector(
        resourcesParser = DefaultStringResParser(),
        readyFileName = "ready.xml",
        translatedFileName = "translated.xml",
    )

    private fun valuesDir(): File = File(tempDir, "values")

    private fun StringLikeResource.value(): String = (this as StringLikeResource.StringRes).value

    @Test
    fun `collects the union of terms from ready and translated`() {
        writeValues(tempDir, "", "ready.xml", "ready_term" to "R")
        writeValues(tempDir, "", "translated.xml", "translated_term" to "T")

        val result = collector.collect(valuesDir())

        assertEquals(setOf("ready_term", "translated_term"), result.mapTo(mutableSetOf()) { it.name })
    }

    @Test
    fun `ready wins on a name collision`() {
        writeValues(tempDir, "", "ready.xml", "dup" to "from ready")
        writeValues(tempDir, "", "translated.xml", "dup" to "from translated")

        val result = collector.collect(valuesDir())

        assertEquals(1, result.size)
        assertEquals("from ready", result.single().value())
    }

    @Test
    fun `collects from translated only when ready is absent`() {
        writeValues(tempDir, "", "translated.xml", "translated_term" to "T")

        val result = collector.collect(valuesDir())

        assertEquals(setOf("translated_term"), result.mapTo(mutableSetOf()) { it.name })
    }

    @Test
    fun `returns an empty set when neither file exists`() {
        val result = collector.collect(valuesDir())

        assertTrue(result.isEmpty())
    }
}
