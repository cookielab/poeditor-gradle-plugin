package io.cookielab.android.poeditor.xml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

internal class StringLikeResourceTest {

    @Test
    fun `StringRes equality is based only on name`() {
        val res1 = StringLikeResource.StringRes("test", "value1")
        val res2 = StringLikeResource.StringRes("test", "value2")

        assertEquals(res1, res2)
        assertEquals(res1.hashCode(), res2.hashCode())
    }

    @Test
    fun `StringRes with different names are not equal`() {
        val res1 = StringLikeResource.StringRes("test1", "value")
        val res2 = StringLikeResource.StringRes("test2", "value")

        assertNotEquals(res1, res2)
    }

    @Test
    fun `StringRes is not equal to different type`() {
        val stringRes = StringLikeResource.StringRes("test", "value")
        val pluralRes = pluralRes("test")

        assertNotEquals<StringLikeResource>(stringRes, pluralRes)
    }

    @Test
    fun `PluralRes equality is based only on name`() {
        val res1 = StringLikeResource.PluralRes(
            "test",
            listOf(StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "one"))
        )
        val res2 = StringLikeResource.PluralRes(
            "test",
            listOf(StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.OTHER, "other"))
        )

        assertEquals(res1, res2)
        assertEquals(res1.hashCode(), res2.hashCode())
    }

    @Test
    fun `PluralRes with different names are not equal`() {
        val res1 = pluralRes("test1")
        val res2 = pluralRes("test2")

        assertNotEquals(res1, res2)
    }

    @Test
    fun `PluralRes is not equal to different type`() {
        val pluralRes = pluralRes("test")
        val stringRes = StringLikeResource.StringRes("test", "value")

        assertNotEquals<StringLikeResource>(pluralRes, stringRes)
    }

    @Test
    fun `resources are equal to themselves and not equal to null`() {
        val stringRes = StringLikeResource.StringRes("test", "value")
        val pluralRes = pluralRes("test")

        assertEquals(stringRes, stringRes)
        assertEquals(pluralRes, pluralRes)
        assertNotEquals<StringLikeResource?>(null, stringRes)
        assertNotEquals<StringLikeResource?>(null, pluralRes)
    }

    @Test
    fun `Quantity xmlName matches the Android plural quantity keywords`() {
        assertEquals("zero", StringLikeResource.PluralRes.Quantity.ZERO.xmlName)
        assertEquals("one", StringLikeResource.PluralRes.Quantity.ONE.xmlName)
        assertEquals("two", StringLikeResource.PluralRes.Quantity.TWO.xmlName)
        assertEquals("few", StringLikeResource.PluralRes.Quantity.FEW.xmlName)
        assertEquals("many", StringLikeResource.PluralRes.Quantity.MANY.xmlName)
        assertEquals("other", StringLikeResource.PluralRes.Quantity.OTHER.xmlName)
    }

    @Test
    fun `PluralRes requires at least one item`() {
        assertFailsWith<IllegalArgumentException> {
            StringLikeResource.PluralRes("empty", emptyList())
        }
    }

    @Test
    fun `PluralRes rejects duplicate quantities`() {
        assertFailsWith<IllegalArgumentException> {
            StringLikeResource.PluralRes(
                "dup",
                listOf(
                    StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "a"),
                    StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "b"),
                ),
            )
        }
    }

    @Test
    fun `PluralRes Item equality includes the value, unlike its parent`() {
        val item1 = StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "one")
        val item2 = StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "one")
        val item3 = StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.ONE, "different")

        assertEquals(item1, item2)
        assertEquals(item1.hashCode(), item2.hashCode())
        assertNotEquals(item1, item3)
    }

    private fun pluralRes(name: String) = StringLikeResource.PluralRes(
        name,
        listOf(StringLikeResource.PluralRes.Item(StringLikeResource.PluralRes.Quantity.OTHER, "v")),
    )
}
