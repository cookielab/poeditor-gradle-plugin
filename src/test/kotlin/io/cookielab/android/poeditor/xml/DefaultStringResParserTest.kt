package io.cookielab.android.poeditor.xml

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class DefaultStringResParserTest {

    @TempDir
    lateinit var tempDir: File

    private val parser = DefaultStringResParser()

    @Test
    fun `parser parses empty resources tag`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parser parses single string resource`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">Test App</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("app_name", stringRes.name)
        assertEquals("Test App", stringRes.value)
    }

    @Test
    fun `parser parses string with XML entities`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="test">&lt;tag&gt; &amp; &quot;quotes&quot;</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("test", stringRes.name)
        assertEquals("&lt;tag&gt; &amp; \"quotes\"", stringRes.value)
    }

    @Test
    fun `parser parses string with CDATA`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="html_content"><![CDATA[<html><body>Test</body></html>]]></string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("html_content", stringRes.name)
        assertEquals("\"<![CDATA[<html><body>Test</body></html>]]>\"", stringRes.value)
    }

    @Test
    fun `parser parses string with mixed CDATA and text`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="mixed">Before <![CDATA[<tag>]]> After</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("mixed", stringRes.name)
        assertEquals("Before \"<![CDATA[<tag>]]>\" After", stringRes.value)
    }

    @Test
    fun `parser keeps special characters verbatim inside CDATA`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="link"><![CDATA[<a href="x?a=1&b=2">link</a>]]></string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("link", stringRes.name)
        // Unlike the ENTITY_REF path, & < > inside CDATA are NOT escaped — they survive verbatim in the wrapper.
        assertEquals("\"<![CDATA[<a href=\"x?a=1&b=2\">link</a>]]>\"", stringRes.value)
    }

    @Test
    fun `parser parses empty string value`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="empty"></string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("empty", stringRes.name)
        assertEquals("", stringRes.value)
    }

    @Test
    fun `parser parses multiple string resources`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="first">First</string>
                <string name="second">Second</string>
                <string name="third">Third</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(3, result.size)
        assertEquals("first", (result[0] as StringLikeResource.StringRes).name)
        assertEquals("second", (result[1] as StringLikeResource.StringRes).name)
        assertEquals("third", (result[2] as StringLikeResource.StringRes).name)
    }

    @Test
    fun `parser parses plurals with all quantities`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <plurals name="test_plural">
                    <item quantity="zero">Zero items</item>
                    <item quantity="one">One item</item>
                    <item quantity="two">Two items</item>
                    <item quantity="few">Few items</item>
                    <item quantity="many">Many items</item>
                    <item quantity="other">%d items</item>
                </plurals>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val pluralRes = assertIs<StringLikeResource.PluralRes>(result[0])
        assertEquals("test_plural", pluralRes.name)
        assertEquals(6, pluralRes.items.size)

        val items = pluralRes.items.associateBy { it.quantity }
        assertEquals("Zero items", items[StringLikeResource.PluralRes.Quantity.ZERO]?.value)
        assertEquals("One item", items[StringLikeResource.PluralRes.Quantity.ONE]?.value)
        assertEquals("Two items", items[StringLikeResource.PluralRes.Quantity.TWO]?.value)
        assertEquals("Few items", items[StringLikeResource.PluralRes.Quantity.FEW]?.value)
        assertEquals("Many items", items[StringLikeResource.PluralRes.Quantity.MANY]?.value)
        assertEquals("%d items", items[StringLikeResource.PluralRes.Quantity.OTHER]?.value)
    }

    @Test
    fun `parser parses plurals with only one item`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <plurals name="minimal">
                    <item quantity="other">Items</item>
                </plurals>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val pluralRes = assertIs<StringLikeResource.PluralRes>(result[0])
        assertEquals("minimal", pluralRes.name)
        assertEquals(1, pluralRes.items.size)
        assertEquals(StringLikeResource.PluralRes.Quantity.OTHER, pluralRes.items.first().quantity)
    }

    @Test
    fun `parser rejects plurals with duplicate quantities`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <plurals name="duplicate_quantity">
                    <item quantity="one">First one</item>
                    <item quantity="one">Second one</item>
                    <item quantity="other">Other value</item>
                </plurals>
            </resources>
        """.trimIndent()

        // Android does not allow repeated quantities; PluralRes enforces this at construction.
        assertFailsWith<IllegalArgumentException> { parser.parseContent(xml) }
    }

    @Test
    fun `parser parses plurals with CDATA in items`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <plurals name="with_cdata">
                    <item quantity="one"><![CDATA[<b>One</b>]]></item>
                    <item quantity="other"><![CDATA[<b>%d</b>]]></item>
                </plurals>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val pluralRes = assertIs<StringLikeResource.PluralRes>(result[0])
        assertEquals(2, pluralRes.items.size)
        val oneItem = pluralRes.items.find { it.quantity == StringLikeResource.PluralRes.Quantity.ONE }
        assertEquals("\"<![CDATA[<b>One</b>]]>\"", oneItem?.value)
        val otherItem = pluralRes.items.find { it.quantity == StringLikeResource.PluralRes.Quantity.OTHER }
        assertEquals("\"<![CDATA[<b>%d</b>]]>\"", otherItem?.value)
    }

    @Test
    fun `parser parses mixed strings and plurals`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="title">Title</string>
                <plurals name="count">
                    <item quantity="one">One</item>
                    <item quantity="other">%d</item>
                </plurals>
                <string name="subtitle">Subtitle</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(3, result.size)
        assertIs<StringLikeResource.StringRes>(result[0])
        assertIs<StringLikeResource.PluralRes>(result[1])
        assertIs<StringLikeResource.StringRes>(result[2])
    }

    @Test
    fun `parser parses string with newlines and whitespace`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="multiline">Line 1
            Line 2
                Line 3</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("Line 1\nLine 2\n    Line 3", stringRes.value)
    }

    @Test
    fun `parser handles XML without xml declaration`() {
        val xml = """
            <resources>
                <string name="test">Value</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        assertEquals("test", (result[0] as StringLikeResource.StringRes).name)
    }

    @Test
    fun `parser parses string with special characters`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="special">Test \n \t \' \" \\</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("special", stringRes.name)
        assertEquals("Test \\n \\t \\' \\\" \\\\", stringRes.value)
    }

    @Test
    fun `parser returns duplicates without filtering`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="duplicate">Value</string>
                <string name="duplicate">Value</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        // Parser returns all items, distinct() is called by the caller
        assertEquals(2, result.size)
    }

    @Test
    fun `parser parses string with apostrophes and quotes`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="quoted">It\'s a "test"</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("It\\'s a \"test\"", stringRes.value)
    }

    @Test
    fun `parser parses string with unicode characters`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="unicode">Unicode: \u2764 \u263A</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("unicode", stringRes.name)
        assertEquals("Unicode: \\u2764 \\u263A", stringRes.value)
    }

    @Test
    fun `parser parses string and plural with the same name`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="test_name">String value</string>
                <plurals name="test_name">
                    <item quantity="one">One item</item>
                    <item quantity="other">%d items</item>
                </plurals>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(2, result.size)

        val strings = result.filterIsInstance<StringLikeResource.StringRes>()
        assertEquals(1, strings.size)
        val stringRes = strings.first()
        assertEquals("test_name", stringRes.name)
        assertEquals("String value", stringRes.value)

        val plurals = result.filterIsInstance<StringLikeResource.PluralRes>()
        assertEquals(1, plurals.size)
        val pluralRes = plurals.first()
        assertEquals("test_name", pluralRes.name)
        assertEquals(2, pluralRes.items.size)
    }

    @Test
    fun `parser parses plural and string with the same name in reverse order`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <plurals name="same_name">
                    <item quantity="one">One</item>
                    <item quantity="other">%d</item>
                </plurals>
                <string name="same_name">String value</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(2, result.size)

        val plurals = result.filterIsInstance<StringLikeResource.PluralRes>()
        assertEquals(1, plurals.size)
        val pluralRes = plurals.first()
        assertEquals("same_name", pluralRes.name)

        val strings = result.filterIsInstance<StringLikeResource.StringRes>()
        assertEquals(1, strings.size)
        val stringRes = strings.first()
        assertEquals("same_name", stringRes.name)
        assertEquals("String value", stringRes.value)
    }

    @Test
    fun `parser parses multiple strings and plurals with overlapping names`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="resource_a">String A</string>
                <plurals name="resource_a">
                    <item quantity="other">Plural A</item>
                </plurals>
                <string name="resource_b">String B</string>
                <plurals name="resource_b">
                    <item quantity="other">Plural B</item>
                </plurals>
                <string name="resource_c">Only string C</string>
            </resources>
        """.trimIndent()

        val result = parser.parseContent(xml)

        assertEquals(5, result.size)

        // Verify all resources are present
        val strings = result.filterIsInstance<StringLikeResource.StringRes>()
        val plurals = result.filterIsInstance<StringLikeResource.PluralRes>()

        assertEquals(3, strings.size)
        assertEquals(2, plurals.size)

        // Verify names
        assertTrue(strings.any { it.name == "resource_a" })
        assertTrue(strings.any { it.name == "resource_b" })
        assertTrue(strings.any { it.name == "resource_c" })
        assertTrue(plurals.any { it.name == "resource_a" })
        assertTrue(plurals.any { it.name == "resource_b" })
    }

    @Test
    fun `parseFile reads and parses the file content`() {
        val file = File(tempDir, "strings.xml")
        file.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="from_file">From file</string>
            </resources>
            """.trimIndent()
        )

        val result = parser.parseFile(file)

        assertEquals(1, result.size)
        val stringRes = assertIs<StringLikeResource.StringRes>(result[0])
        assertEquals("from_file", stringRes.name)
        assertEquals("From file", stringRes.value)
    }

    @Test
    fun `parser throws when a string is encountered outside resources`() {
        val xml = """<string name="orphan">value</string>"""

        assertFailsWith<IllegalStateException> { parser.parseContent(xml) }
    }

    @Test
    fun `parser throws when an item is encountered outside plurals`() {
        val xml = """
            <resources>
                <item quantity="one">value</item>
            </resources>
        """.trimIndent()

        assertFailsWith<IllegalStateException> { parser.parseContent(xml) }
    }

    @Test
    fun `parser throws when a string has no name attribute`() {
        val xml = """
            <resources>
                <string>value</string>
            </resources>
        """.trimIndent()

        assertFailsWith<IllegalStateException> { parser.parseContent(xml) }
    }

    @Test
    fun `parser throws when a plurals has no name attribute`() {
        val xml = """
            <resources>
                <plurals>
                    <item quantity="one">value</item>
                </plurals>
            </resources>
        """.trimIndent()

        assertFailsWith<IllegalStateException> { parser.parseContent(xml) }
    }

    @Test
    fun `parser throws when a plural item has an unknown quantity`() {
        val xml = """
            <resources>
                <plurals name="count">
                    <item quantity="dozen">value</item>
                </plurals>
            </resources>
        """.trimIndent()

        assertFailsWith<IllegalStateException> { parser.parseContent(xml) }
    }
}
