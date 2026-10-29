package dev.izumi.appopsnext.settings

import android.app.UiModeManager
import android.content.Context

enum class AppTheme(
    val nightMode: Int,
) {
    SYSTEM(UiModeManager.MODE_NIGHT_AUTO),
    LIGHT(UiModeManager.MODE_NIGHT_NO),
    DARK(UiModeManager.MODE_NIGHT_YES),
    ;

    companion object {
        fun fromName(name: String?): AppTheme? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Hands the choice to the system, which keeps it for this app and applies it
 * before the first frame. The window theme and status bar follow along with
 * the Compose colours, so there is no light flash on a dark launch.
 */
class ApplicationThemeManager(
    context: Context,
) {
    private val uiModeManager =
        context.getSystemService(UiModeManager::class.java)

    fun setTheme(theme: AppTheme) {
        uiModeManager.setApplicationNightMode(theme.nightMode)
    }
}
