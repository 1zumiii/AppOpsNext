package dev.izumi.appopsnext.presentation.history

import androidx.annotation.DrawableRes
import dev.izumi.appopsnext.R
import java.util.Locale

/** Add a mapping here when a permission needs a distinct visual cue. */
internal object HistoryPermissionIconCatalog {
    private val icons = mapOf(
        "CAMERA" to R.drawable.ic_permission_camera,
        "RECORD_AUDIO" to R.drawable.ic_permission_microphone,
        "FINE_LOCATION" to R.drawable.ic_permission_location,
        "COARSE_LOCATION" to R.drawable.ic_permission_location,
        "READ_CLIPBOARD" to R.drawable.ic_permission_clipboard,
        "WRITE_CLIPBOARD" to R.drawable.ic_permission_clipboard,
    )

    @DrawableRes
    fun iconFor(operationName: String): Int {
        val key = operationName.removePrefix("android:").uppercase(Locale.ROOT)
        return icons[key] ?: R.drawable.ic_action_manage
    }
}
