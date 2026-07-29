package dev.izumi.appopsnext.history

import dev.izumi.appopsnext.history.model.HistoryPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryPermissionOrderingTest {
    private val camera = HistoryPermission("CAMERA")
    private val microphone = HistoryPermission("RECORD_AUDIO")
    private val clipboard = HistoryPermission("READ_CLIPBOARD")
    private val available = listOf(camera, microphone, clipboard)

    @Test
    fun `adding a permission preserves current custom order`() {
        assertEquals(
            listOf(microphone, camera, clipboard),
            HistoryPermissionOrdering.mergeSelection(
                current = listOf(microphone, camera),
                requestedOperationNames = listOf(
                    camera.shellOperationName,
                    microphone.shellOperationName,
                    clipboard.shellOperationName,
                ),
                available = available,
            ),
        )
    }

    @Test
    fun `removing a permission preserves retained order`() {
        assertEquals(
            listOf(clipboard, camera),
            HistoryPermissionOrdering.mergeSelection(
                current = listOf(clipboard, microphone, camera),
                requestedOperationNames = listOf(
                    camera.shellOperationName,
                    clipboard.shellOperationName,
                ),
                available = available,
            ),
        )
    }

    @Test
    fun `reorder requires the exact current selection`() {
        assertEquals(
            listOf(clipboard, camera, microphone),
            HistoryPermissionOrdering.reorder(
                current = available,
                orderedOperationNames = listOf(
                    clipboard.shellOperationName,
                    camera.shellOperationName,
                    microphone.shellOperationName,
                ),
            ),
        )
        assertNull(
            HistoryPermissionOrdering.reorder(
                current = available,
                orderedOperationNames = listOf(
                    camera.shellOperationName,
                    microphone.shellOperationName,
                ),
            ),
        )
    }
}
