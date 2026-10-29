package dev.izumi.appopsnext.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    @Test fun `the latest page redirect names the release tag`() {
        assertEquals(
            "v1.6.5",
            UpdateChecker.tagOfReleaseUrl("https://github.com/1zumiii/AppOpsNext/releases/tag/v1.6.5"),
        )
    }

    @Test fun `a beta build is offered the highest published version`() {
        val releases = listOf(
            PublishedRelease("v1.6.6-beta3", null, draft = true),
            PublishedRelease("v1.6.6-beta2", null, draft = false),
            PublishedRelease("v1.6.6-beta1", null, draft = false),
            PublishedRelease("v1.6.5", null, draft = false),
        )
        assertEquals("v1.6.6-beta2", UpdateChecker.newestRelease(releases)?.tag)
    }

    @Test fun `a release outranks the betas that led to it`() {
        val releases = listOf(
            PublishedRelease("v1.6.6", null, draft = false),
            PublishedRelease("v1.6.6-beta2", null, draft = false),
        )
        assertEquals("v1.6.6", UpdateChecker.newestRelease(releases.reversed())?.tag)
        assertNull(UpdateChecker.newestRelease(emptyList()))
    }

    @Test fun `a redirect elsewhere names no tag`() {
        assertNull(UpdateChecker.tagOfReleaseUrl("https://github.com/1zumiii/AppOpsNext/releases"))
        assertNull(UpdateChecker.tagOfReleaseUrl("https://github.com/someone/Other/releases/tag/v9.9.9"))
        assertNull(UpdateChecker.tagOfReleaseUrl("http://github.com/1zumiii/AppOpsNext/releases/tag/v9.9.9"))
        assertNull(UpdateChecker.tagOfReleaseUrl("https://example.com/1zumiii/AppOpsNext/releases/tag/v9.9.9"))
        assertNull(UpdateChecker.tagOfReleaseUrl("not a url"))
    }
}
