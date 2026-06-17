package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.DefaultLocaleResolver
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class DefaultLocaleResolverTest {

    private val resolver = DefaultLocaleResolver()

    @Test
    fun `returns the language mapped to the default-locale qualifier`() {
        val language = resolver.resolveDefaultLanguage(mapOf("" to "en", "-cs" to "cs"))

        assertEquals("en", language)
    }

    @Test
    fun `throws GradleException when the default-locale mapping is absent`() {
        val exception = assertFailsWith<GradleException> {
            resolver.resolveDefaultLanguage(mapOf("-cs" to "cs"))
        }

        assertContains(exception.message.orEmpty(), "Default locale (\"\") mapping wasn't provided")
    }
}
