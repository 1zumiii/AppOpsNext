package dev.izumi.appopsnext.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.util.Locale

@Composable
fun AppOpsNextTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val language = configuration.locales[0]?.language
    val systemColors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    // Groups sit a step above the page in both modes, with the user's dynamic
    // accent kept. Light mode lifts them towards white. Dark mode keeps the
    // page at the darkest tone so the groups stay lighter rather than sinking
    // into it.
    val colorScheme = if (darkTheme) {
        systemColors.copy(
            background = systemColors.surfaceContainerLowest,
            surface = systemColors.surfaceContainerLowest,
            surfaceContainerLow = systemColors.surfaceContainer,
            surfaceContainer = systemColors.surfaceContainerHigh,
            // The dynamic accent container is a saturated block on a dark page.
            // Mixed mostly into the raised surface it marks a selection quietly.
            primaryContainer = lerp(
                systemColors.surfaceContainerHighest,
                systemColors.primaryContainer,
                0.4f,
            ),
        )
    } else {
        systemColors.copy(
            background = systemColors.surfaceContainerLow,
            surface = systemColors.surfaceContainerLow,
            surfaceContainerLow = systemColors.surfaceContainerLowest,
        )
    }

    // The activity keeps running across a light/dark change, so the window
    // theme no longer switches the system bar icons. They are set here instead.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (darkTheme) 0 else lightBars, lightBars)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = when (language) {
            Locale.ENGLISH.language -> CompactEnglishTypography
            Locale.CHINESE.language -> BalancedChineseTypography
            else -> AppDefaultTypography
        },
        content = { ThemeTransitionHost(content) },
    )
}
