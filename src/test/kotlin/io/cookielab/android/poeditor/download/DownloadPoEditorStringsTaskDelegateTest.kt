package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.common.TermsDownloader
import io.cookielab.android.poeditor.xml.StringLikeResource
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DownloadPoEditorStringsTaskDelegateTest {

    @Test
    fun `throws GradleException when default locale is missing`() {
        val fakeDownloader = FakeTermsDownloader(emptyMap())
        val fakeProcessor = FakeProjectProcessor()

        val delegate = DownloadPoEditorStringsTaskDelegate(
            termsDownloader = fakeDownloader,
            projectProcessor = fakeProcessor,
        )

        val exception = assertFailsWith<GradleException> {
            delegate.execute(
                subprojects = emptyList(),
                qualifiersToLanguages = mapOf("-cs" to "cs"), // Missing default ""
            )
        }

        assertContains(exception.message.orEmpty(), "Default locale (\"\") mapping wasn't provided")
        // The prerequisite check must short-circuit before any download or processing happens.
        assertFalse(fakeDownloader.downloadTermsCalled)
        assertFalse(fakeProcessor.processSubprojectsCalled)
    }

    @Test
    fun `executes successfully with default locale present`() {
        val downloadedTerms = mapOf(
            "en" to listOf(StringLikeResource.StringRes("test", "value"))
        )
        val fakeDownloader = FakeTermsDownloader(downloadedTerms)
        val fakeProcessor = FakeProjectProcessor()

        val delegate = DownloadPoEditorStringsTaskDelegate(
            termsDownloader = fakeDownloader,
            projectProcessor = fakeProcessor,
        )

        delegate.execute(
            subprojects = emptyList(),
            qualifiersToLanguages = mapOf("" to "en"), // Valid default locale
        )

        assertTrue(fakeDownloader.downloadTermsCalled)
        assertTrue(fakeProcessor.processSubprojectsCalled)
        assertEquals(emptyList(), fakeProcessor.capturedSubprojects)
        assertEquals(downloadedTerms, fakeProcessor.capturedDownloadedTerms)
    }

    @Test
    fun `passes all parameters correctly to processor`() {
        val subprojects = listOf(
            SubprojectInfo(":app", setOf("/path/to/res")),
            SubprojectInfo(":lib", setOf("/path/to/lib/res")),
        )
        val qualifiersToLanguages = mapOf(
            "" to "en",
            "-cs" to "cs",
            "-de" to "de",
        )
        val downloadedTerms = mapOf(
            "en" to listOf(StringLikeResource.StringRes("key1", "value1")),
            "cs" to listOf(StringLikeResource.StringRes("key1", "hodnota1")),
            "de" to listOf(StringLikeResource.StringRes("key1", "wert1")),
        )

        val fakeDownloader = FakeTermsDownloader(downloadedTerms)
        val fakeProcessor = FakeProjectProcessor()

        val delegate = DownloadPoEditorStringsTaskDelegate(
            termsDownloader = fakeDownloader,
            projectProcessor = fakeProcessor,
        )

        delegate.execute(
            subprojects = subprojects,
            qualifiersToLanguages = qualifiersToLanguages,
        )

        assertEquals(subprojects, fakeProcessor.capturedSubprojects)
        assertEquals(qualifiersToLanguages, fakeProcessor.capturedQualifiersToLanguages)
        assertEquals(downloadedTerms, fakeProcessor.capturedDownloadedTerms)
        assertEquals("en", fakeProcessor.capturedDefaultLanguage)
    }

    @Test
    fun `handles empty subprojects list`() {
        val fakeDownloader = FakeTermsDownloader(emptyMap())
        val fakeProcessor = FakeProjectProcessor()

        val delegate = DownloadPoEditorStringsTaskDelegate(
            termsDownloader = fakeDownloader,
            projectProcessor = fakeProcessor,
        )

        delegate.execute(
            subprojects = emptyList(),
            qualifiersToLanguages = mapOf("" to "en"),
        )

        assertTrue(fakeProcessor.processSubprojectsCalled)
        assertEquals(emptyList(), fakeProcessor.capturedSubprojects)
    }

    @Test
    fun `handles empty downloaded terms`() {
        val fakeDownloader = FakeTermsDownloader(emptyMap())
        val fakeProcessor = FakeProjectProcessor()

        val delegate = DownloadPoEditorStringsTaskDelegate(
            termsDownloader = fakeDownloader,
            projectProcessor = fakeProcessor,
        )

        delegate.execute(
            subprojects = listOf(SubprojectInfo(":app", setOf("/res"))),
            qualifiersToLanguages = mapOf("" to "en"),
        )

        assertTrue(fakeProcessor.processSubprojectsCalled)
        assertEquals(emptyMap(), fakeProcessor.capturedDownloadedTerms)
    }

    @Test
    fun `does not process subprojects when downloading terms fails`() {
        val fakeDownloader = FailingTermsDownloader()
        val fakeProcessor = FakeProjectProcessor()

        val delegate = DownloadPoEditorStringsTaskDelegate(
            termsDownloader = fakeDownloader,
            projectProcessor = fakeProcessor,
        )

        assertFailsWith<IllegalStateException> {
            delegate.execute(
                subprojects = emptyList(),
                qualifiersToLanguages = mapOf("" to "en"),
            )
        }

        assertFalse(fakeProcessor.processSubprojectsCalled)
    }

    // Test doubles

    private class FakeTermsDownloader(
        private val termsToReturn: Map<String, List<StringLikeResource>>,
    ) : TermsDownloader {
        var downloadTermsCalled = false

        override fun downloadTerms(): Map<String, List<StringLikeResource>> {
            downloadTermsCalled = true
            return termsToReturn
        }
    }

    private class FailingTermsDownloader : TermsDownloader {
        override fun downloadTerms(): Map<String, List<StringLikeResource>> {
            error("download failed")
        }
    }

    private class FakeProjectProcessor : DownloadStringsProjectProcessor {
        var processSubprojectsCalled = false
        var capturedSubprojects: List<SubprojectInfo>? = null
        var capturedQualifiersToLanguages: Map<String, String>? = null
        var capturedDownloadedTerms: Map<String, List<StringLikeResource>>? = null
        var capturedDefaultLanguage: String? = null

        override fun processSubprojects(
            subprojects: List<SubprojectInfo>,
            qualifiersToLanguages: Map<String, String>,
            downloadedTerms: Map<String, List<StringLikeResource>>,
            defaultLanguage: String,
        ) {
            processSubprojectsCalled = true
            capturedSubprojects = subprojects
            capturedQualifiersToLanguages = qualifiersToLanguages
            capturedDownloadedTerms = downloadedTerms
            capturedDefaultLanguage = defaultLanguage
        }
    }
}
