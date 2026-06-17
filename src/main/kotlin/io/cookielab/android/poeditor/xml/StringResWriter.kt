package io.cookielab.android.poeditor.xml

import java.io.File

/**
 * Interface for writing string-like resources to files.
 */
internal interface StringResWriter {

    /**
     * Creates a new [file] if it doesn't exist and writes the given [resources] to it.
     *
     * @param resources list of resources to write
     * @param file the file to write to
     * @param printDate whether to print date or not
     */
    fun createAndWrite(resources: List<StringLikeResource>, file: File, printDate: Boolean)
}
