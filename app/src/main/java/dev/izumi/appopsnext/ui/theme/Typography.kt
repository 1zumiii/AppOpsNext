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

val BalancedChineseTypography = DefaultTypography.copy(
    titleLarge = DefaultTypography.titleLarge.smaller(CHINESE_FONT_SIZE_REDUCTION_SP),
    titleMedium = DefaultTypography.titleMedium.smaller(CHINESE_FONT_SIZE_REDUCTION_SP),
    titleSmall = DefaultTypography.titleSmall.smaller(CHINESE_FONT_SIZE_REDUCTION_SP),
    bodyLarge = DefaultTypography.bodyLarge.smaller(CHINESE_FONT_SIZE_REDUCTION_SP),
    bodyMedium = DefaultTypography.bodyMedium.smaller(CHINESE_FONT_SIZE_REDUCTION_SP),
    labelLarge = DefaultTypography.labelLarge.smaller(CHINESE_FONT_SIZE_REDUCTION_SP),
    labelMedium = DefaultTypography.labelMedium.smaller(CHINESE_FONT_SIZE_REDUCTION_SP),
)

val AppDefaultTypography: Typography = DefaultTypography

private fun TextStyle.smaller(reductionSp: Float = ENGLISH_FONT_SIZE_REDUCTION_SP): TextStyle = copy(
    fontSize = (fontSize.value - reductionSp).sp,
)

private const val ENGLISH_FONT_SIZE_REDUCTION_SP = 1f
private const val CHINESE_FONT_SIZE_REDUCTION_SP = 0.5f
