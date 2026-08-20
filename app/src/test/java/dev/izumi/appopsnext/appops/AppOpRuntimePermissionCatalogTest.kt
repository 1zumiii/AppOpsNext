package dev.izumi.appopsnext.appops

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppOpRuntimePermissionCatalogTest {
    @Test
    fun `camera operation resolves its Android runtime permission`() {
        assertEquals(
            Manifest.permission.CAMERA,
            AppOpRuntimePermissionCatalog.requiredPermission(
                "android:camera",
            ),
        )
    }

    @Test
    fun `tracking-only operation has no runtime permission requirement`() {
        assertNull(
            AppOpRuntimePermissionCatalog.requiredPermission(
                "android:read_clipboard",
            ),
        )
    }
}
