package io.cookielab.android.poeditor.download

import java.io.File

/** Writes a values[qualifier]/[fileName] resource file holding the given string resources. */
internal fun writeValues(resDir: File, qualifier: String, fileName: String, vararg strings: Pair<String, String>) {
    val dir = File(resDir, "values$qualifier").apply { mkdirs() }
    val body = strings.joinToString(separator = "\n") { (name, value) ->
        """    <string name="$name">$value</string>"""
    }
    File(dir, fileName).writeText("<resources>\n$body\n</resources>")
}

internal fun translatedFile(resDir: File, qualifier: String = ""): File =
    File(resDir, "values$qualifier/translated.xml")

internal fun readyFile(resDir: File, qualifier: String = ""): File =
    File(resDir, "values$qualifier/ready.xml")
