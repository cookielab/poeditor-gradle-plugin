package io.cookielab.android.poeditor.xml

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class FileStringResWriterTest {

    @TempDir
    lateinit var tempDir: File

    private val writer = FileStringResWriter(indent = "    ")
    private val parser = DefaultStringResParser()

    @Test
    fun `writes a string resource`() {
        val file = File(tempDir, "strings.xml")

        writer.createAndWrite(
            resources = listOf(StringLikeResource.StringRes("app_name", "My App")),
            file = file,
            printDate = false,
        )

        val expected = listOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<resources>",
            """    <string name="app_name">My App</string>""",
            "</resources>",
            "",
        ).joinToString(separator = "\n")
        assertEquals(expected, file.readText())
    }

    @Test
    fun `writes a plurals resource`() {
        val file = File(tempDir, "strings.xml")

        writer.createAndWrite(
            resources = listOf(
                StringLikeResource.PluralRes(
                    name = "items",
                    items = listOf(
                        StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "%d item"),
                        StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.OTHER, "%d items"),
                    ),
                ),
            ),
            file = file,
            printDate = false,
        )

        val expected = listOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<resources>",
            """    <plurals name="items">""",
            """        <item quantity="one">%d item</item>""",
            """        <item quantity="other">%d items</item>""",
            "    </plurals>",
            "</resources>",
            "",
        ).joinToString(separator = "\n")
        assertEquals(expected, file.readText())
    }

    @Test
    fun `writes an empty resources block when there are no resources`() {
        val file = File(tempDir, "strings.xml")

        writer.createAndWrite(resources = emptyList(), file = file, printDate = false)

        val expected = listOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<resources>",
            "</resources>",
            "",
        ).joinToString(separator = "\n")
        assertEquals(expected, file.readText())
    }

    @Test
    fun `adds an import comment when printDate is true`() {
        val file = File(tempDir, "strings.xml")

        writer.createAndWrite(
            resources = listOf(StringLikeResource.StringRes("app_name", "My App")),
            file = file,
            printDate = true,
        )

        assertTrue(file.readText().contains("<!-- Imported from POEditor on "))
    }

    @Test
    fun `writes the import date comment from the injected clock`() {
        val clock = Clock.fixed(Instant.parse("2026-06-16T00:00:00Z"), ZoneOffset.UTC)
        val file = File(tempDir, "strings.xml")

        FileStringResWriter(indent = "    ", clock = clock).createAndWrite(
            resources = listOf(StringLikeResource.StringRes("app_name", "My App")),
            file = file,
            printDate = true,
        )

        val expected = listOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<!-- Imported from POEditor on 2026-06-16T00:00Z -->",
            "<resources>",
            """    <string name="app_name">My App</string>""",
            "</resources>",
            "",
        ).joinToString(separator = "\n")
        assertEquals(expected, file.readText())
    }

    @Test
    fun `creates the file when it does not exist yet`() {
        val file = File(tempDir, "strings.xml")
        assertFalse(file.exists())

        writer.createAndWrite(
            resources = listOf(StringLikeResource.StringRes("app_name", "My App")),
            file = file,
            printDate = false,
        )

        assertTrue(file.exists())
    }

    @Test
    fun `overwrites the previous content of an existing file`() {
        val file = File(tempDir, "strings.xml")
        file.writeText("stale content that must not survive")

        writer.createAndWrite(
            resources = listOf(StringLikeResource.StringRes("app_name", "My App")),
            file = file,
            printDate = false,
        )

        val expected = listOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<resources>",
            """    <string name="app_name">My App</string>""",
            "</resources>",
            "",
        ).joinToString(separator = "\n")
        assertEquals(expected, file.readText())
    }

    @Test
    fun `writes multiple mixed string and plurals resources in order`() {
        val file = File(tempDir, "strings.xml")

        writer.createAndWrite(
            resources = listOf(
                StringLikeResource.StringRes("first", "First"),
                StringLikeResource.PluralRes(
                    name = "items",
                    items = listOf(
                        StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "%d item"),
                        StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.OTHER, "%d items"),
                    ),
                ),
                StringLikeResource.StringRes("last", "Last"),
            ),
            file = file,
            printDate = false,
        )

        val expected = listOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<resources>",
            """    <string name="first">First</string>""",
            """    <plurals name="items">""",
            """        <item quantity="one">%d item</item>""",
            """        <item quantity="other">%d items</item>""",
            "    </plurals>",
            """    <string name="last">Last</string>""",
            "</resources>",
            "",
        ).joinToString(separator = "\n")
        assertEquals(expected, file.readText())
    }

    @Test
    fun `writes every supported plural quantity using its xml name`() {
        val file = File(tempDir, "strings.xml")

        writer.createAndWrite(
            resources = listOf(
                StringLikeResource.PluralRes(
                    name = "count",
                    items = StringLikeResource.PluralRes.Quantity.entries.map { quantity ->
                        StringLikeResource.PluralRes.Item(quantity, quantity.xmlName)
                    },
                ),
            ),
            file = file,
            printDate = false,
        )

        val expected = listOf(
            """<?xml version="1.0" encoding="utf-8"?>""",
            "<resources>",
            """    <plurals name="count">""",
            """        <item quantity="zero">zero</item>""",
            """        <item quantity="one">one</item>""",
            """        <item quantity="two">two</item>""",
            """        <item quantity="few">few</item>""",
            """        <item quantity="many">many</item>""",
            """        <item quantity="other">other</item>""",
            "    </plurals>",
            "</resources>",
            "",
        ).joinToString(separator = "\n")
        assertEquals(expected, file.readText())
    }

    @Test
    fun `string and plural round-trip through writer and parser preserve names and values`() {
        val resources = listOf(
            StringLikeResource.StringRes("greeting", "Hello"),
            StringLikeResource.PluralRes(
                "count",
                listOf(
                    StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "%d item"),
                    StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.OTHER, "%d items"),
                ),
            ),
        )
        val file = File(tempDir, "strings.xml")
        writer.createAndWrite(resources = resources, file = file, printDate = false)

        val parsed = parser.parseFile(file)

        val string = parsed.filterIsInstance<StringLikeResource.StringRes>().single()
        assertEquals("greeting", string.name)
        assertEquals("Hello", string.value)
        val plural = parsed.filterIsInstance<StringLikeResource.PluralRes>().single()
        assertEquals("count", plural.name)
        assertEquals(
            listOf(
                StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "%d item"),
                StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.OTHER, "%d items"),
            ),
            plural.items,
        )
    }
}
