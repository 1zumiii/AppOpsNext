package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.appops.model.AppOpEntry
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.assertEquals
import org.junit.Test

class AppOpDisplayCatalogTest {
    private val labels = mapOf(
        R.string.app_op_label_camera to "相机",
        R.string.app_op_label_run_in_background to
            "后台运行",
    )
    private val englishLabels = mapOf(
        R.string.app_op_label_camera to "Camera",
        R.string.app_op_label_run_in_background to "Run in background",
    )

    @Test
    fun `uid entry replaces duplicate package entry as effective state`() {
        val items = AppOpDisplayCatalog.build(
            entries = listOf(
                entry("CAMERA", "allow", isUid = false),
                entry("CAMERA", "ignore", isUid = true),
            ),
            query = "",
            labelResolver = ::label,
            alternateLabelResolver = ::englishLabel,
        )

        assertEquals(1, items.size)
        assertEquals(AppOpScope.UID, items.single().scope)
        assertEquals("ignore", items.single().mode)
    }

    @Test
    fun `common privacy operations sort before background and unknown ops`() {
        val items = AppOpDisplayCatalog.build(
            entries = listOf(
                entry("VENDOR_PRIVATE_OP", "allow"),
                entry("RUN_IN_BACKGROUND", "allow"),
                entry("CAMERA", "ignore"),
            ),
            query = "",
            labelResolver = ::label,
            alternateLabelResolver = ::englishLabel,
        )

        assertEquals(
            listOf("CAMERA", "RUN_IN_BACKGROUND", "VENDOR_PRIVATE_OP"),
            items.map(AppOpDisplayItem::operationName),
        )
    }

    @Test
    fun `search matches chinese english and raw operation names`() {
        val entries = listOf(
            entry("CAMERA", "ignore"),
            entry("RUN_IN_BACKGROUND", "allow"),
        )

        assertEquals(
            listOf("CAMERA"),
            build(entries, "相机"),
        )
        assertEquals(
            listOf("CAMERA"),
            build(entries, "camera"),
        )
        assertEquals(
            listOf("RUN_IN_BACKGROUND"),
            build(entries, "run_in"),
        )
    }

    private fun build(
        entries: List<AppOpEntry>,
        query: String,
    ): List<String> =
        AppOpDisplayCatalog
            .build(entries, query, ::label, ::englishLabel)
            .map(AppOpDisplayItem::operationName)

    private fun label(resourceId: Int): String =
        requireNotNull(labels[resourceId])

    private fun englishLabel(resourceId: Int): String =
        requireNotNull(englishLabels[resourceId])

    private fun entry(
        name: String,
        mode: String,
        isUid: Boolean = false,
    ) = AppOpEntry(
        name = name,
        mode = mode,
        details = null,
        hasUidModePrefix = isUid,
    )
}
