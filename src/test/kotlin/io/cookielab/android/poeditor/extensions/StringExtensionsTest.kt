package io.cookielab.android.poeditor.extensions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class StringExtensionsTest {

    @Test
    fun `single LSEP char is replaced by a single newline`() {
        val input = "From the player.\n${LSEP}Create a new scene or select additional saved scenes below."

        val expected = "From the player.\n\nCreate a new scene or select additional saved scenes below."

        assertEquals(expected, input.withoutInvalidNewlineChars())
    }

    @Test
    fun `multiple LSEP chars are replaced by the same amount of newlines`() {
        val input = "From the player.\n$LSEP${LSEP}Create a new scene or ${LSEP}select additional below.$LSEP"

        val expected = "From the player.\n\n\nCreate a new scene or \nselect additional below.\n"

        assertEquals(expected, input.withoutInvalidNewlineChars())
    }

    @Test
    fun `single PSEP char is replaced by a single newline`() {
        val input = "From the player.\n${PSEP}Create a new scene or select additional saved scenes below."

        val expected = "From the player.\n\nCreate a new scene or select additional saved scenes below."

        assertEquals(expected, input.withoutInvalidNewlineChars())
    }

    @Test
    fun `multiple PSEP chars are replaced by the same amount of newlines`() {
        val input = "From the player.\n${PSEP}${PSEP}Create a new scene or ${PSEP}select below.$PSEP"

        val expected = "From the player.\n\n\nCreate a new scene or \nselect below.\n"

        assertEquals(expected, input.withoutInvalidNewlineChars())
    }

    @Test
    fun `multiple different invalid newline chars are replaced by the same amount of newlines`() {
        val input = "This sentence${LSEP}contains multiple\n${LSEP}${PSEP}different${PSEP}\n${PSEP}separators."
        val expected = "This sentence\ncontains multiple\n\n\ndifferent\n\n\nseparators."

        assertEquals(expected, input.withoutInvalidNewlineChars())
    }

    companion object {
        private const val LSEP = '\u2028'
        private const val PSEP = '\u2029'
    }
}
