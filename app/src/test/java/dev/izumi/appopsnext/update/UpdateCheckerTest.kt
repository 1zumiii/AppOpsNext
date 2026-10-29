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

    @Test fun `a redirect elsewhere names no tag`() {
        assertNull(UpdateChecker.tagOfReleaseUrl("https://github.com/1zumiii/AppOpsNext/releases"))
        assertNull(UpdateChecker.tagOfReleaseUrl("https://github.com/someone/Other/releases/tag/v9.9.9"))
        assertNull(UpdateChecker.tagOfReleaseUrl("http://github.com/1zumiii/AppOpsNext/releases/tag/v9.9.9"))
        assertNull(UpdateChecker.tagOfReleaseUrl("https://example.com/1zumiii/AppOpsNext/releases/tag/v9.9.9"))
        assertNull(UpdateChecker.tagOfReleaseUrl("not a url"))
    }
}
