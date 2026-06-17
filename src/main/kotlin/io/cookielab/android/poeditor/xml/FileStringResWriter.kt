package io.cookielab.android.poeditor.xml

import org.gradle.api.GradleException
import java.io.File
import java.time.Clock
import java.time.OffsetDateTime

internal class FileStringResWriter(
    private val indent: String,
    private val clock: Clock = Clock.systemDefaultZone(),
) : StringResWriter {

    override fun createAndWrite(resources: List<StringLikeResource>, file: File, printDate: Boolean) {
        val content = getNewFileContent(resources, printDate)
        val parent = file.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw GradleException("Could not create parent directory '${parent.absolutePath}'.")
        }
        file.writeText(content.joinToString(separator = "\n"))
    }

    private fun getNewFileContent(strings: List<StringLikeResource>, printDate: Boolean): List<String> {
        return buildList {
            add("""<?xml version="1.0" encoding="utf-8"?>""")
            if (printDate) add("<!-- Imported from POEditor on ${OffsetDateTime.now(clock)} -->")
            add("<resources>")
            strings.flatMap { getStringDefinition(it) }.forEach { add("$indent$it") }
            add("</resources>")
            add("")
        }
    }

    private fun getStringDefinition(string: StringLikeResource): List<String> {
        return when (string) {
            is StringLikeResource.StringRes -> listOf("""<string name="${string.name}">${string.value}</string>""")
            is StringLikeResource.PluralRes -> buildList {
                add("""<plurals name="${string.name}">""")
                string.items.forEach { (quantity, value) ->
                    add("""$indent<item quantity="${quantity.xmlName}">$value</item>""")
                }
                add("</plurals>")
            }
        }
    }
}
