package io.cookielab.android.poeditor.common

import io.cookielab.android.poeditor.extensions.AnsiColor
import io.cookielab.android.poeditor.extensions.inColor
import io.cookielab.android.poeditor.json.PoeditorExportResponse
import io.cookielab.android.poeditor.xml.StringLikeResource
import io.cookielab.android.poeditor.xml.StringResParser
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.gradle.api.logging.Logger
import java.io.IOException

@Suppress("LongParameterList")
internal class PoEditorTermsDownloader(
    private val resourcesParser: StringResParser,
    private val excludedSuffices: Set<String>,
    private val projectId: String,
    private val token: String,
    private val languages: Set<String>,
    private val logger: Logger,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor { message ->
                // The api_token is POSTed in the request body; mask it so it never reaches the logs.
                logger.debug(message.replace(API_TOKEN_REGEX, "api_token=***"))
            }
                .apply { level = HttpLoggingInterceptor.Level.BODY },
        )
        .build(),
    private val json: Json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    },
    private val baseUrl: String = "https://api.poeditor.com/",
) : TermsDownloader {

    override fun downloadTerms(): Map<String, List<StringLikeResource>> {
        val result = languages.associateWith { language -> downloadLanguage(language) }

        val distinctTerms = result.values.flatten().distinct().size
        logger.lifecycle(
            "Downloaded {} unique terms across {} languages from POEditor.".inColor(AnsiColor.GREEN),
            distinctTerms,
            languages.size,
        )
        return result
    }

    @Suppress("ThrowsCount")
    private fun downloadLanguage(language: String): List<StringLikeResource> {
        logger.info("Downloading '$language'…")

        // Step 1: Request export from POEditor
        val exportRequest = Request.Builder()
            .url("${baseUrl}v2/projects/export")
            .post(
                FormBody.Builder()
                    .add("id", projectId)
                    .add("api_token", token)
                    .add("language", language)
                    .add("type", "android_strings")
                    .add("order", "terms")
                    .build()
            )
            .build()

        val exportResponse = httpClient.newCall(exportRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to export from POEditor: ${response.code} ${response.message}")
            }
            val responseBody = response.body.string()
            json.decodeFromString<PoeditorExportResponse>(responseBody)
        }

        // POEditor signals application-level errors (e.g. an invalid token) with HTTP 200 and a
        // non-200 code in the body, so the HTTP status check above isn't enough.
        if (exportResponse.response.code != OK_CODE) {
            throw IOException(
                "POEditor export failed for language '$language': " +
                    "${exportResponse.response.code} ${exportResponse.response.message}"
            )
        }

        // Step 2: Download the actual XML from the export URL
        val result = requireNotNull(exportResponse.result) {
            "POEditor returned a success code but no export result for language '$language'."
        }
        val xmlRequest = Request.Builder()
            .url(result.url)
            .get()
            .build()

        val xmlContent = httpClient.newCall(xmlRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download XML: ${response.code} ${response.message}")
            }
            response.body.string()
        }

        // Step 3: Parse and filter
        val parsed = resourcesParser.parseContent(xmlContent).distinct()
        val filtered = filterExcludedSuffices(parsed)

        logger.lifecycle(
            "Download completed for language '{}'. Parsed: {}. Filtered out: {}",
            language,
            parsed.size,
            parsed.size - filtered.size,
        )

        return filtered
    }

    /**
     * Filters the given [input] list so that no strings ending with any of [excludedSuffices] is returned.
     */
    private fun filterExcludedSuffices(input: List<StringLikeResource>): List<StringLikeResource> {
        if (excludedSuffices.isEmpty()) {
            return input
        }
        return input.filterNot { res ->
            excludedSuffices.any { suffix ->
                res.name.endsWith(suffix)
            }
        }
    }

    companion object {
        private const val OK_CODE = "200"
        private val API_TOKEN_REGEX = Regex("api_token=[^&]*")
    }
}
