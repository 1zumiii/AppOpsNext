package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.presentation.app_detail.AppOpDisplayCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPermissionIconCatalogTest {
    @Test
    fun `every catalogued permission has its own icon`() {
        val unmapped = AppOpDisplayCatalog.knownOperations()
            .map { it.shellName }
            .filter {
                HistoryPermissionIconCatalog.visualFor(it).iconRes == R.drawable.ic_ph_shield
            }

        assertEquals(emptyList<String>(), unmapped)
    }

    @Test
    fun `stable and shell names resolve to the same visual`() {
        assertEquals(
            HistoryPermissionIconCatalog.visualFor("READ_MEDIA_IMAGES"),
            HistoryPermissionIconCatalog.visualFor("android:read_media_images"),
        )
    }

    @Test
    fun `unknown operations fall back to the neutral shield`() {
        assertEquals(
            HistoryPermissionVisual(R.drawable.ic_ph_shield, HistoryPermissionTone.NEUTRAL),
            HistoryPermissionIconCatalog.visualFor("SOME_VENDOR_OPERATION"),
        )
    }
}
