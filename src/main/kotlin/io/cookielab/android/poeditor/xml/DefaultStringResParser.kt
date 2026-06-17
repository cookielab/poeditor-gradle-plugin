package io.cookielab.android.poeditor.xml

import io.cookielab.android.poeditor.extensions.sanitized
import org.gradle.api.GradleException
import org.kobjects.ktxml.api.EventType
import org.kobjects.ktxml.mini.MiniXmlPullParser
import java.io.File

/**
 * Class used for parsing string-like resources from files or text.
 */
internal class DefaultStringResParser : StringResParser {

    @Suppress("TooGenericExceptionCaught")
    override fun parseFile(file: File): List<StringLikeResource> {
        return try {
            parseContent(file.readText())
        } catch (e: Exception) {
            throw GradleException("Failed to parse string resources from '${file.absolutePath}'.", e)
        }
    }

    /**
     * Parsing is done using a pull-parser; [MiniXmlPullParser] was chosen because it allows easy CDATA detection.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    override fun parseContent(input: String): List<StringLikeResource> {
        val parser = MiniXmlPullParser(input.iterator(), relaxed = true)

        var eventType = parser.eventType
        var isInsideResources = false
        var isInsideString = false
        var isInsidePlurals = false
        var isInsidePluralItem = false

        var resName: String? = null
        var pluralItemQuantity: String? = null
        val rawTextContentBuilder = StringBuilder()
        val pluralItems = mutableListOf<StringLikeResource.PluralRes.Item>()

        val result = mutableListOf<StringLikeResource>()

        while (eventType != EventType.END_DOCUMENT) {
            when (eventType) {
                EventType.START_TAG -> {
                    when (parser.name) {
                        "resources" -> isInsideResources = true
                        "string" -> {
                            check(isInsideResources) { "Isn't inside resources" }
                            resName = parser.getAttributeValue(namespace = "", name = "name")
                            isInsideString = true
                        }

                        "plurals" -> {
                            check(isInsideResources) { "Isn't inside resources" }
                            resName = parser.getAttributeValue(namespace = "", name = "name")
                            isInsidePlurals = true
                        }

                        "item" -> {
                            check(isInsidePlurals) { "Isn't inside plurals" }
                            pluralItemQuantity = parser.getAttributeValue(namespace = "", name = "quantity")
                            isInsidePluralItem = true
                        }
                        // Inline markup inside a <string> (e.g. <b>, <xliff:g>) is not modeled: such tags are
                        // ignored here while their text content is still captured by the TEXT branch below.
                    }
                }

                EventType.END_TAG -> {
                    when (parser.name) {
                        "resources" -> isInsideResources = false
                        "string" -> {
                            check(isInsideResources) { "Isn't inside resources" }
                            // create string
                            val stringName = checkNotNull(resName) { "resName is null.[string]" }
                            result.add(
                                StringLikeResource.StringRes(
                                    name = stringName,
                                    value = rawTextContentBuilder.toString().sanitized(),
                                )
                            )
                            resName = null
                            isInsideString = false
                        }

                        "plurals" -> {
                            check(isInsideResources) { "Isn't inside resources" }
                            // create plurals
                            val pluralName = checkNotNull(resName) { "resName is null.[plural]" }
                            result.add(
                                StringLikeResource.PluralRes(
                                    name = pluralName,
                                    items = pluralItems.toList(),
                                )
                            )
                            pluralItems.clear()
                            resName = null
                            isInsidePlurals = false
                        }

                        "item" -> {
                            check(isInsidePlurals) { "Isn't inside plurals" }
                            val quantity = StringLikeResource.PluralRes.Quantity.entries
                                .firstOrNull { it.xmlName == pluralItemQuantity }
                                ?: error("Unknown plural quantity '$pluralItemQuantity' in resource '$resName'.")
                            pluralItems.add(
                                StringLikeResource.PluralRes.Item(
                                    quantity = quantity,
                                    value = rawTextContentBuilder.toString().sanitized(),
                                )
                            )
                            pluralItemQuantity = null
                            isInsidePluralItem = false
                        }
                    }
                    rawTextContentBuilder.clear()
                }

                EventType.TEXT -> {
                    if (isInsideString || isInsidePluralItem) {
                        rawTextContentBuilder.append(parser.text)
                    }
                }

                EventType.CDSECT -> {
                    /* MiniXmlPullParser strips the <![CDATA[ ... ]]> delimiters and reports only the inner
                    text. We re-wrap it and surround it with double quotes so the value survives as a literal
                    Android string resource. */
                    if (isInsideString || isInsidePluralItem) {
                        rawTextContentBuilder
                            .append("\"<![CDATA[")
                            .append(parser.text)
                            .append("]]>\"")
                    }
                }

                EventType.ENTITY_REF -> {
                    if (isInsideString || isInsidePluralItem) {
                        rawTextContentBuilder.append(sanitizeValue(parser.text))
                    }
                }

                EventType.PROCESSING_INSTRUCTION -> {}
                EventType.IGNORABLE_WHITESPACE -> {}
                EventType.COMMENT -> {}
                EventType.DOCDECL -> {}
                EventType.XML_DECL -> {}
                EventType.START_DOCUMENT -> {}
                EventType.END_DOCUMENT -> {}
            }
            eventType = parser.nextToken()
        }

        return result
    }

    /**
     * Escapes raw XML special characters back into entity references so the value stays well-formed:
     * - "&" -> "&amp;"
     * - "<" -> "&lt;"
     * - ">" -> "&gt;"
     */
    private fun sanitizeValue(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
