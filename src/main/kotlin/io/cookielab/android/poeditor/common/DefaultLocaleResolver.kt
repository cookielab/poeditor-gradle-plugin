package io.cookielab.android.poeditor.common

import org.gradle.api.GradleException

internal class DefaultLocaleResolver : LocaleResolver {

    override fun resolveDefaultLanguage(qualifiersToLanguages: Map<String, String>): String {
        return qualifiersToLanguages[LocaleResolver.DEFAULT_LOCALE_QUALIFIER]
            ?: throw GradleException(MISSING_DEFAULT_LOCALE_MESSAGE)
    }

    companion object {
        private val MISSING_DEFAULT_LOCALE_MESSAGE = """
            Default locale ("") mapping wasn't provided. This is a configuration error.

            Please set
            ```
            poEditorSync {
                qualifiersToLanguages = mapOf(
                    "" to "your-default-locale",
                )
                ...
            }
            ```
            in your top level build.gradle(.kts).
        """.trimIndent()
    }
}
