package dev.izumi.appopsnext.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test fun `a higher patch release is newer`() {
        assertTrue(AppVersion.isNewer("1.3.4", "1.3.3"))
        assertFalse(AppVersion.isNewer("1.3.3", "1.3.4"))
    }

    @Test fun `the same version is not newer`() {
        assertFalse(AppVersion.isNewer("1.3.3", "1.3.3"))
        assertFalse(AppVersion.isNewer("v1.3.3", "1.3.3"))
    }

    @Test fun `segments are compared as numbers rather than text`() {
        // A string comparison would rank 1.3.9 above 1.3.10 here.
        assertTrue(AppVersion.isNewer("1.3.10", "1.3.9"))
        assertFalse(AppVersion.isNewer("1.3.9", "1.3.10"))
        assertTrue(AppVersion.isNewer("1.10.0", "1.9.9"))
    }

    @Test fun `a leading v on either side is ignored`() {
        assertTrue(AppVersion.isNewer("v1.4.0", "1.3.3"))
        assertTrue(AppVersion.isNewer("V2.0.0", "v1.9.9"))
    }

    @Test fun `a prerelease does not outrank the release it precedes`() {
        assertFalse(AppVersion.isNewer("1.4.0-rc1", "1.4.0"))
        assertTrue(AppVersion.isNewer("1.4.0", "1.4.0-rc1"))
    }

    @Test fun `a shorter version is not newer than a longer one that extends it`() {
        assertFalse(AppVersion.isNewer("1.4", "1.4.1"))
        assertTrue(AppVersion.isNewer("1.4.1", "1.4"))
    }

    @Test fun `unparsable input never claims an update is available`() {
        assertFalse(AppVersion.isNewer("", "1.3.3"))
        assertFalse(AppVersion.isNewer("nightly", "1.3.3"))
    }
}
