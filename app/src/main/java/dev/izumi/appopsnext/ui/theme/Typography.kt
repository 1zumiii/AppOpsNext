package dev.izumi.appopsnext.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

val BalancedChineseTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.chinese(),
    displayMedium = DefaultTypography.displayMedium.chinese(),
    displaySmall = DefaultTypography.displaySmall.chinese(),
    headlineLarge = DefaultTypography.headlineLarge.chinese(),
    headlineMedium = DefaultTypography.headlineMedium.chinese(),
    headlineSmall = DefaultTypography.headlineSmall.chinese(),
    titleLarge = DefaultTypography.titleLarge.chinese().copy(
        fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium,
    ),
    titleMedium = DefaultTypography.titleMedium.chinese().copy(
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = DefaultTypography.titleSmall.chinese().copy(
        fontSize = 13.sp, lineHeight = 20.sp,
    ),
    bodyLarge = DefaultTypography.bodyLarge.chinese().copy(
        fontSize = 15.sp, lineHeight = 23.sp,
    ),
    bodyMedium = DefaultTypography.bodyMedium.chinese().copy(
        fontSize = 13.sp, lineHeight = 20.sp,
    ),
    bodySmall = DefaultTypography.bodySmall.chinese().copy(
        fontSize = 12.sp, lineHeight = 18.sp,
    ),
    labelLarge = DefaultTypography.labelLarge.chinese(),
    labelMedium = DefaultTypography.labelMedium.chinese(),
    labelSmall = DefaultTypography.labelSmall.chinese(),
)

/** Chinese keeps the system font, which users may have chosen themselves, without Latin tracking. */
private fun TextStyle.chinese(): TextStyle = copy(letterSpacing = 0.sp)

val AppDefaultTypography: Typography = DefaultTypography

private fun TextStyle.smaller(reductionSp: Float = ENGLISH_FONT_SIZE_REDUCTION_SP): TextStyle = copy(
    fontSize = (fontSize.value - reductionSp).sp,
)

private const val ENGLISH_FONT_SIZE_REDUCTION_SP = 1f
