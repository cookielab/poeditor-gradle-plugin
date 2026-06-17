package io.cookielab.android.poeditor.common

/**
 * Resolves the POEditor language configured for the default locale.
 */
internal interface LocaleResolver {

    /**
     * Returns the POEditor language mapped to the default-locale (`""`) qualifier in [qualifiersToLanguages].
     *
     * @param qualifiersToLanguages mapping of Android resource qualifiers to POEditor languages
     * @return the language mapped to the default-locale qualifier
     * @throws org.gradle.api.GradleException if no default-locale mapping is configured (a configuration error)
     */
    fun resolveDefaultLanguage(qualifiersToLanguages: Map<String, String>): String

    companion object {
        /** The Android resource qualifier for the default locale — an empty string (the bare `values` directory). */
        const val DEFAULT_LOCALE_QUALIFIER: String = ""
    }
}
