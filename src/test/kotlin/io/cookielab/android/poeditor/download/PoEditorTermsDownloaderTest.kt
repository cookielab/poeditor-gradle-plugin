package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.PoEditorTermsDownloader
import io.cookielab.android.poeditor.json.PoeditorExportResponse
import io.cookielab.android.poeditor.xml.DefaultStringResParser
import io.cookielab.android.poeditor.xml.StringLikeResource
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.gradle.api.logging.Logger
import java.io.IOException
import java.net.URLDecoder
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class PoEditorTermsDownloaderTest {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    @MockK
    private lateinit var logger: Logger

    // Instance field (not a shared companion object): JUnit creates a new test instance per method,
    // so each test gets its own server with an empty recorded-request queue.
    private val webServer = MockWebServer()

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        webServer.start()
    }

    @AfterTest
    fun tearDown() {
        webServer.close()
    }

    @Test
    fun `terms downloader correctly parses the response for a single language`() {
        val baseUrl = webServer.url("").toString()
        val language = "en"
        val token = "token"
        val projectId = "projectId"
        val downloader = createDownloader(languages = setOf(language), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(body = getExportResponse(baseUrl)))
        webServer.enqueue(MockResponse(body = getNormalXmlResponse()))

        val downloadedTerms = downloader.downloadTerms()

        assertEquals(4, downloadedTerms[language]?.size)

        webServer.validateExportRequest(
            language = language,
            token = token,
            projectId = projectId,
        )
        webServer.validateXmlRequest()
    }

    @Test
    fun `terms downloader correctly filters out unwanted terms`() {
        val baseUrl = webServer.url("").toString()
        val language = "en"
        val token = "token"
        val projectId = "projectId"
        val downloader = createDownloader(languages = setOf(language), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(body = getExportResponse(baseUrl)))
        webServer.enqueue(MockResponse(body = getXmlResponseWithIgnoredTerms("_ios")))

        val downloadedTerms = downloader.downloadTerms()

        assertEquals(4, downloadedTerms[language]?.size)

        webServer.validateExportRequest(
            language = language,
            token = token,
            projectId = projectId,
        )
        webServer.validateXmlRequest()
    }

    @Test
    fun `terms downloader correctly filters out duplicities`() {
        val baseUrl = webServer.url("").toString()
        val language = "en"
        val token = "token"
        val projectId = "projectId"
        val downloader = createDownloader(languages = setOf(language), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(body = getExportResponse(baseUrl)))
        webServer.enqueue(MockResponse(body = getXmlResponseWithDuplicates()))

        val downloadedTerms = downloader.downloadTerms()

        assertEquals(4, downloadedTerms[language]?.size)
        // distinct() keeps the first occurrence of each name, in order — verify which terms survived.
        assertEquals(
            listOf("test-string-1", "test-string-2", "test-string-3", "test-string-4"),
            downloadedTerms[language]?.map { it.name },
        )

        webServer.validateExportRequest(
            language = language,
            token = token,
            projectId = projectId,
        )
        webServer.validateXmlRequest()
    }

    @Test
    fun `terms downloader correctly parses strings and plurals even with the same name`() {
        val baseUrl = webServer.url("").toString()
        val language = "en"
        val token = "token"
        val projectId = "projectId"
        val downloader = createDownloader(languages = setOf(language), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(body = getExportResponse(baseUrl)))
        webServer.enqueue(MockResponse(body = getXmlResponseWithPluralsAsDuplicates()))

        val downloadedTerms = downloader.downloadTerms()

        assertEquals(5, downloadedTerms[language]?.size)
        assertEquals(1, downloadedTerms[language]?.filterIsInstance<StringLikeResource.PluralRes>()?.size)

        webServer.validateExportRequest(
            language = language,
            token = token,
            projectId = projectId,
        )
        webServer.validateXmlRequest()
    }

    @Test
    fun `terms downloader keeps the surviving terms when filtering excluded suffices`() {
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(body = getExportResponse(webServer.url("").toString())))
        webServer.enqueue(MockResponse(body = getXmlResponseWithIgnoredTerms("_ios")))

        val downloadedTerms = downloader.downloadTerms()

        val names = downloadedTerms["en"].orEmpty().map { it.name }
        assertEquals(
            listOf("test-string-1", "test-string-2", "test-string-3", "test-string-4"),
            names,
        )
        assertFalse(names.any { it.endsWith("_ios") })
    }

    @Test
    fun `terms downloader keeps every term when no suffices are excluded`() {
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = emptySet())

        webServer.enqueue(MockResponse(body = getExportResponse(webServer.url("").toString())))
        webServer.enqueue(MockResponse(body = getXmlResponseWithIgnoredTerms("_ios")))

        val downloadedTerms = downloader.downloadTerms()

        // Nothing is filtered out, so both _ios terms survive on top of the four regular ones.
        assertEquals(6, downloadedTerms["en"]?.size)
    }

    @Test
    fun `terms downloader downloads every requested language`() {
        val downloader = createDownloader(languages = setOf("en", "cs"), excludedSuffices = setOf("_ios"))
        val baseUrl = webServer.url("").toString()

        // Two requests per language, in insertion order: en then cs.
        webServer.enqueue(MockResponse(body = getExportResponse(baseUrl)))
        webServer.enqueue(MockResponse(body = getNormalXmlResponse()))
        webServer.enqueue(MockResponse(body = getExportResponse(baseUrl)))
        webServer.enqueue(MockResponse(body = getNormalXmlResponse()))

        val downloadedTerms = downloader.downloadTerms()

        assertEquals(setOf("en", "cs"), downloadedTerms.keys)
        assertEquals(4, downloadedTerms["en"]?.size)
        assertEquals(4, downloadedTerms["cs"]?.size)

        // Each language issues an export request followed by an XML download.
        webServer.validateExportRequest(language = "en", token = "token", projectId = "projectId")
        webServer.validateXmlRequest()
        webServer.validateExportRequest(language = "cs", token = "token", projectId = "projectId")
        webServer.validateXmlRequest()
    }

    @Test
    fun `terms downloader throws when the export request fails`() {
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(code = 500, body = "boom"))

        val exception = assertFailsWith<IOException> { downloader.downloadTerms() }
        assertContains(exception.message.orEmpty(), "Failed to export from POEditor")
    }

    @Test
    fun `terms downloader throws when the xml download fails`() {
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(body = getExportResponse(webServer.url("").toString())))
        webServer.enqueue(MockResponse(code = 404, body = "not found"))

        val exception = assertFailsWith<IOException> { downloader.downloadTerms() }
        assertContains(exception.message.orEmpty(), "Failed to download XML")
    }

    @Test
    fun `terms downloader throws when the export response code indicates failure`() {
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = setOf("_ios"))

        // POEditor returns HTTP 200 with a fail status in the body for e.g. an invalid token.
        val failBody = json.encodeToString(
            PoeditorExportResponse(
                response = PoeditorExportResponse.Response(
                    status = "fail",
                    code = "4011",
                    message = "Invalid API Token",
                ),
                result = PoeditorExportResponse.Result(url = ""),
            ),
        )
        webServer.enqueue(MockResponse(body = failBody))

        val exception = assertFailsWith<IOException> { downloader.downloadTerms() }
        assertContains(exception.message.orEmpty(), "POEditor export failed for language 'en': 4011 Invalid API Token")
    }

    @Test
    fun `terms downloader throws when the export response has an error code and no result`() {
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = setOf("_ios"))

        // Real POEditor errors (e.g. an invalid token) carry an error code and omit "result" entirely.
        val failBody = """{"response":{"status":"fail","code":"4011","message":"Invalid API Token"}}"""
        webServer.enqueue(MockResponse(body = failBody))

        val exception = assertFailsWith<IOException> { downloader.downloadTerms() }
        assertContains(exception.message.orEmpty(), "4011")
    }

    @Test
    fun `terms downloader throws when the export response is not valid JSON`() {
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = setOf("_ios"))

        webServer.enqueue(MockResponse(body = "not json"))

        assertFailsWith<SerializationException> { downloader.downloadTerms() }
    }

    @Test
    fun `terms downloader returns an empty map when no languages are requested`() {
        val downloader = createDownloader(languages = emptySet(), excludedSuffices = setOf("_ios"))

        assertTrue(downloader.downloadTerms().isEmpty())
    }

    @Test
    fun `terms downloader never logs the raw api_token`() {
        val token = "super-secret-token"
        val downloader = createDownloader(languages = setOf("en"), excludedSuffices = setOf("_ios"), token = token)
        val debugMessages = mutableListOf<String>()
        every { logger.debug(capture(debugMessages)) } just Runs

        webServer.enqueue(MockResponse(body = getExportResponse(webServer.url("").toString())))
        webServer.enqueue(MockResponse(body = getNormalXmlResponse()))

        downloader.downloadTerms()

        // BODY-level logging ran: the api_token field is masked and the raw token never appears.
        assertTrue(debugMessages.any { it.contains("api_token=***") })
        assertFalse(debugMessages.any { it.contains(token) })
    }

    private fun createDownloader(
        languages: Set<String>,
        excludedSuffices: Set<String>,
        token: String = "token",
        projectId: String = "projectId",
    ): PoEditorTermsDownloader {
        return PoEditorTermsDownloader(
            excludedSuffices = excludedSuffices,
            resourcesParser = DefaultStringResParser(),
            projectId = projectId,
            token = token,
            languages = languages,
            logger = logger,
            baseUrl = webServer.url("").toString(),
            json = json,
        )
    }

    private fun MockWebServer.validateExportRequest(language: String, token: String, projectId: String) {
        val request = takeRequest()
        assertEquals("/v2/projects/export", request.url.encodedPath)
        val utf8Body = request.body?.utf8().orEmpty()
        val decodedBody = URLDecoder.decode(utf8Body, Charsets.UTF_8)
            .split('&')
            .map { it.split("=", limit = 2) }
            .associate { (k, v) -> k to v }
        assertTrue(decodedBody.isNotEmpty())
        assertEquals(language, decodedBody["language"])
        assertEquals(token, decodedBody["api_token"])
        assertEquals(projectId, decodedBody["id"])
    }

    private fun MockWebServer.validateXmlRequest() {
        val request = takeRequest()
        assertEquals("/downloadXML", request.url.encodedPath)
    }

    private fun getExportResponse(baseUrl: String): String {
        return json.encodeToString(
            PoeditorExportResponse(
                response = PoeditorExportResponse.Response(
                    status = "success",
                    code = "200",
                    message = "OK",
                ),
                result = PoeditorExportResponse.Result(
                    url = "${baseUrl}downloadXML",
                )
            ),
        )
    }

    private fun getNormalXmlResponse(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="test-string-1">"This is the first string"</string>
                <string name="test-string-2">"This is the second string"</string>
                <string name="test-string-3">"This is the third string"</string>
                <string name="test-string-4">"This is the fourth string"</string>
            </resources>
        """.trimIndent()
    }

    private fun getXmlResponseWithIgnoredTerms(ignoredSuffix: String): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="test-string-1">"This is the first string"</string>
                <string name="test-string-2">"This is the second string"</string>
                <string name="test-string-3">"This is the third string"</string>
                <string name="test-string-4">"This is the fourth string"</string>
                <string name="test-string-4$ignoredSuffix">"This is the fourth string (ignored)"</string>
                <string name="test-string-5$ignoredSuffix">"This is the fourth string (ignored)"</string>
            </resources>
        """.trimIndent()
    }

    private fun getXmlResponseWithDuplicates(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="test-string-1">"This is the first string"</string>
                <string name="test-string-2">"This is the second string"</string>
                <string name="test-string-3">"This is the third string"</string>
                <string name="test-string-4">"This is the fourth string"</string>
                <string name="test-string-4">"This is the fourth string"</string>
                <string name="test-string-2">"This is the second string"</string>
            </resources>
        """.trimIndent()
    }

    private fun getXmlResponseWithPluralsAsDuplicates(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="test-string-1">"This is the first string"</string>
                <string name="test-string-2">"This is the second string"</string>
                <string name="test-string-3">"This is the third string"</string>
                <string name="test-string-4">"This is the fourth string"</string>
                <plurals name="test-string-5">
                    <item quantity="other">"This is a dummy item in a plural that is a duplicate of a string"</item>
                </plurals>
            </resources>
        """.trimIndent()
    }
}
