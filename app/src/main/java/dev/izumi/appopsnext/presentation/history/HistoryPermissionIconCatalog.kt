package dev.izumi.appopsnext.presentation.history

import androidx.annotation.DrawableRes
import dev.izumi.appopsnext.R
import java.util.Locale

internal enum class HistoryPermissionTone { PRIMARY, SECONDARY, TERTIARY, NEUTRAL }

internal data class HistoryPermissionVisual(
    @DrawableRes val iconRes: Int,
    val tone: HistoryPermissionTone,
)

/** Add a mapping here when a permission needs a distinct visual cue. */
internal object HistoryPermissionIconCatalog {
    private val visuals = mapOf(
        "CAMERA" to HistoryPermissionVisual(
            R.drawable.ic_ph_camera, HistoryPermissionTone.PRIMARY,
        ),
        "RECORD_AUDIO" to HistoryPermissionVisual(
            R.drawable.ic_ph_microphone, HistoryPermissionTone.TERTIARY,
        ),
        "FINE_LOCATION" to HistoryPermissionVisual(
            R.drawable.ic_ph_map_pin, HistoryPermissionTone.SECONDARY,
        ),
        "COARSE_LOCATION" to HistoryPermissionVisual(
            R.drawable.ic_ph_map_pin, HistoryPermissionTone.SECONDARY,
        ),
        "READ_CLIPBOARD" to HistoryPermissionVisual(
            R.drawable.ic_ph_clipboard_text, HistoryPermissionTone.NEUTRAL,
        ),
        "WRITE_CLIPBOARD" to HistoryPermissionVisual(
            R.drawable.ic_ph_clipboard_text, HistoryPermissionTone.NEUTRAL,
        ),
    )

    fun visualFor(operationName: String): HistoryPermissionVisual {
        val key = operationName.removePrefix("android:").uppercase(Locale.ROOT)
        return visuals[key] ?: HistoryPermissionVisual(
            R.drawable.ic_ph_shield, HistoryPermissionTone.NEUTRAL,
        )
    }
}
