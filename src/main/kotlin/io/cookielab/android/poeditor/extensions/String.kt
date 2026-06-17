package io.cookielab.android.poeditor.extensions

internal fun String.sanitized(): String {
    return withoutInvalidUnicodeChars().withoutInvalidNewlineChars()
}

/**
 * Removing invalid unicode characters, as described in [XML 1.0 spec](https://www.w3.org/TR/REC-xml/#charsets).
 */
internal fun String.withoutInvalidUnicodeChars(): String {
    return replace("[^\u0009\u000A\u000D\u0020-\uD7FF\uE000-\uFFFD\u10000-\u10FFF]+".toRegex(), "")
}

/**
 * Replaces the invalid Unicode newline chars (invalid == Android can't draw them) with just a basic newline.
 *
 * Replaced characters currently include:
 * - `\u2028`: Line Separator (LS, LSEP)
 * - `\u2029`: Paragraph Separator (PS, PSEP)
 */
internal fun String.withoutInvalidNewlineChars(): String {
    return replace("[\u2028\u2029]".toRegex(), "\n")
}

internal fun String.inColor(color: AnsiColor): String {
    return "\u001B[${color.prefix}m$this\u001B[0m"
}

@Suppress("MagicNumber")
internal enum class AnsiColor(val prefix: Int) {
    BLACK(30),
    RED(31),
    GREEN(32),
    YELLOW(33),
    BLUE(34),
    MAGENTA(35),
    CYAN(36),
    WHITE(37),
}
