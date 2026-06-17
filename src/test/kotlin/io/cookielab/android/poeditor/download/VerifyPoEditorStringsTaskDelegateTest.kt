package io.cookielab.android.poeditor.download

import io.cookielab.android.poeditor.common.SubprojectInfo
import io.cookielab.android.poeditor.common.TermsDownloader
import io.cookielab.android.poeditor.verify.ReadyTermsVerifier
import io.cookielab.android.poeditor.verify.VerifyPoEditorStringsTaskDelegate
import io.cookielab.android.poeditor.xml.StringLikeResource
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class VerifyPoEditorStringsTaskDelegateTest {

    @Test
    fun `throws GradleException listing pending terms when verifier reports any`() {
        val fakeDownloader = FakeTermsDownloader(mapOf("en" to listOf(StringLikeResource.StringRes("term_a", "A"))))
        val fakeVerifier = FakeReadyTermsVerifier(mapOf(":app" to listOf("term_b", "term_c")))

        val delegate = VerifyPoEditorStringsTaskDelegate(fakeDownloader, fakeVerifier, defaultLanguage = "en")

        val exception = assertFailsWith<GradleException> {
            delegate.execute(subprojects = listOf(SubprojectInfo(":app", setOf("/res"))))
        }

        assertContains(exception.message.orEmpty(), ":app")
        assertContains(exception.message.orEmpty(), "term_b")
        assertContains(exception.message.orEmpty(), "term_c")
    }

    @Test
    fun `succeeds when verifier reports no pending terms`() {
        val fakeDownloader = FakeTermsDownloader(mapOf("en" to listOf(StringLikeResource.StringRes("term_a", "A"))))
        val fakeVerifier = FakeReadyTermsVerifier(emptyMap())

        val delegate = VerifyPoEditorStringsTaskDelegate(fakeDownloader, fakeVerifier, defaultLanguage = "en")

        delegate.execute(subprojects = listOf(SubprojectInfo(":app", setOf("/res"))))

        assertTrue(fakeVerifier.verifyCalled)
    }

    @Test
    fun `throws GradleException when POEditor returns no terms for the default language`() {
        val fakeDownloader = FakeTermsDownloader(mapOf("en" to emptyList()))
        val fakeVerifier = FakeReadyTermsVerifier(emptyMap())

        val delegate = VerifyPoEditorStringsTaskDelegate(fakeDownloader, fakeVerifier, defaultLanguage = "en")

        val exception = assertFailsWith<GradleException> {
            delegate.execute(subprojects = listOf(SubprojectInfo(":app", setOf("/res"))))
        }

        assertContains(exception.message.orEmpty(), "no terms")
        // Nothing to verify against an empty/failed export.
        assertFalse(fakeVerifier.verifyCalled)
    }

    @Test
    fun `passes the default language terms to the verifier`() {
        val englishTerms = listOf(StringLikeResource.StringRes("term_a", "A"))
        val fakeDownloader = FakeTermsDownloader(
            mapOf(
                "en" to englishTerms,
                "cs" to listOf(StringLikeResource.StringRes("term_a", "Hodnota")),
            ),
        )
        val fakeVerifier = FakeReadyTermsVerifier(emptyMap())
        val subprojects = listOf(SubprojectInfo(":app", setOf("/res")))

        val delegate = VerifyPoEditorStringsTaskDelegate(fakeDownloader, fakeVerifier, defaultLanguage = "en")

        delegate.execute(subprojects = subprojects)

        assertEquals(subprojects, fakeVerifier.capturedSubprojects)
        assertEquals(englishTerms, fakeVerifier.capturedDownloadedDefaultTerms)
    }

    // Test doubles

    private class FakeTermsDownloader(
        private val termsToReturn: Map<String, List<StringLikeResource>>,
    ) : TermsDownloader {
        override fun downloadTerms(): Map<String, List<StringLikeResource>> {
            return termsToReturn
        }
    }

    private class FakeReadyTermsVerifier(
        private val resultToReturn: Map<String, List<String>>,
    ) : ReadyTermsVerifier {
        var verifyCalled = false
        var capturedSubprojects: List<SubprojectInfo>? = null
        var capturedDownloadedDefaultTerms: List<StringLikeResource>? = null

        override fun verify(
            subprojects: List<SubprojectInfo>,
            downloadedDefaultTerms: List<StringLikeResource>,
        ): Map<String, List<String>> {
            verifyCalled = true
            capturedSubprojects = subprojects
            capturedDownloadedDefaultTerms = downloadedDefaultTerms
            return resultToReturn
        }
    }
}
