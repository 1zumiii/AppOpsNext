package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.history.model.HistoryPermissionDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPermissionSelectionCodecTest {
    @Test
    fun `missing preference uses four default permissions`() {
        assertEquals(
            HistoryPermissionDefaults.permissions,
            HistoryPermissionSelectionCodec.decode(null),
        )
    }

    @Test
    fun `selection round trip preserves order`() {
        val selection = listOf(
            HistoryPermission("CAMERA"),
            HistoryPermission("READ_CLIPBOARD"),
            HistoryPermission("RECORD_AUDIO"),
        )

        assertEquals(
            selection,
            HistoryPermissionSelectionCodec.decode(
                HistoryPermissionSelectionCodec.encode(selection),
            ),
        )
    }

    @Test
    fun `decode normalizes and removes duplicate operation names`() {
        assertEquals(
            listOf(
                HistoryPermission("CAMERA"),
                HistoryPermission("FINE_LOCATION"),
            ),
            HistoryPermissionSelectionCodec.decode(
                "android:camera\nCAMERA\nfine_location",
            ),
        )
    }
}
