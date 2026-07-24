package dev.izumi.appopsnext.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val DefaultTypography = Typography()

val CompactEnglishTypography = DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge.smaller(),
    displayMedium = DefaultTypography.displayMedium.smaller(),
    displaySmall = DefaultTypography.displaySmall.smaller(),
    headlineLarge = DefaultTypography.headlineLarge.smaller(),
    headlineMedium = DefaultTypography.headlineMedium.smaller(),
    headlineSmall = DefaultTypography.headlineSmall.smaller(),
    titleLarge = DefaultTypography.titleLarge.smaller(),
    titleMedium = DefaultTypography.titleMedium.smaller(),
    titleSmall = DefaultTypography.titleSmall.smaller(),
    bodyLarge = DefaultTypography.bodyLarge.smaller(),
    bodyMedium = DefaultTypography.bodyMedium.smaller(),
    bodySmall = DefaultTypography.bodySmall.smaller(),
    labelLarge = DefaultTypography.labelLarge.smaller(),
    labelMedium = DefaultTypography.labelMedium.smaller(),
    labelSmall = DefaultTypography.labelSmall.smaller(),
)

val AppDefaultTypography: Typography = DefaultTypography

private fun TextStyle.smaller(): TextStyle = copy(
    fontSize = (fontSize.value - ENGLISH_FONT_SIZE_REDUCTION_SP).sp,
)

private const val ENGLISH_FONT_SIZE_REDUCTION_SP = 1f
