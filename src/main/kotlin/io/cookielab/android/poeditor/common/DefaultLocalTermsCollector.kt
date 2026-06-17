package io.cookielab.android.poeditor.common

import io.cookielab.android.poeditor.xml.StringLikeResource
import io.cookielab.android.poeditor.xml.StringResParser
import java.io.File

internal class DefaultLocalTermsCollector(
    private val resourcesParser: StringResParser,
    private val readyFileName: String,
    private val translatedFileName: String,
) : LocalTermsCollector {

    override fun collect(valuesDir: File): Set<StringLikeResource> {
        val result = mutableSetOf<StringLikeResource>()
        val readyFile = File(valuesDir, readyFileName)
        if (readyFile.exists()) {
            result.addAll(resourcesParser.parseFile(readyFile))
        }
        val translatedFile = File(valuesDir, translatedFileName)
        if (translatedFile.exists()) {
            result.addAll(resourcesParser.parseFile(translatedFile))
        }
        return result
    }
}
