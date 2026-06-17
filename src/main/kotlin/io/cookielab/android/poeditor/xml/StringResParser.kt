package io.cookielab.android.poeditor.xml

import java.io.File

/**
 * Interface for parsing string-like resources from files or text.
 */
internal interface StringResParser {

    /**
     * Parses the list of [StringLikeResource] from the given [file].
     *
     * @param file the file to parse
     * @return list of parsed resources
     */
    fun parseFile(file: File): List<StringLikeResource>

    /**
     * Parses the given string [input] into the list of [StringLikeResource].
     *
     * @param input XML content as string
     * @return list of parsed resources
     */
    fun parseContent(input: String): List<StringLikeResource>
}
