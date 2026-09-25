package dev.izumi.appopsnext.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.util.Locale

private val MainChineseTypography = BalancedChineseTypography.copy(
    titleLarge = BalancedChineseTypography.titleLarge.copy(
        fontSize = 20.5.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = BalancedChineseTypography.titleMedium.copy(letterSpacing = 0.sp),
    titleSmall = BalancedChineseTypography.titleSmall.copy(letterSpacing = 0.sp),
    bodyLarge = BalancedChineseTypography.bodyLarge.copy(
        lineHeight = 25.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = BalancedChineseTypography.bodyMedium.copy(
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = BalancedChineseTypography.bodySmall.copy(
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = BalancedChineseTypography.labelLarge.copy(letterSpacing = 0.sp),
    labelMedium = BalancedChineseTypography.labelMedium.copy(letterSpacing = 0.sp),
    labelSmall = BalancedChineseTypography.labelSmall.copy(letterSpacing = 0.sp),
)

@Composable
fun MainPageTypography(content: @Composable () -> Unit) {
    if (isChineseMainPage()) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = MainChineseTypography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    } else {
        content()
    }
}

@Composable
fun mainPageHeadingWeight(): FontWeight =
    if (isChineseMainPage()) FontWeight.Medium else FontWeight.SemiBold

@Composable
private fun isChineseMainPage(): Boolean =
    LocalConfiguration.current.locales[0]?.language == Locale.CHINESE.language
