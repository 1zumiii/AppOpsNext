package dev.izumi.appopsnext.ui.theme

import androidx.compose.ui.text.TextStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyTest {
    @Test
    fun `every English text style is one sp smaller`() {
        val defaultStyles = AppDefaultTypography.styles()
        val englishStyles = CompactEnglishTypography.styles()

        defaultStyles.zip(englishStyles).forEach { (default, english) ->
            assertEquals(
                default.fontSize.value - 1f,
                english.fontSize.value,
                0f,
            )
        }
    }

    private fun androidx.compose.material3.Typography.styles():
        List<TextStyle> = listOf(
            displayLarge,
            displayMedium,
            displaySmall,
            headlineLarge,
            headlineMedium,
            headlineSmall,
            titleLarge,
            titleMedium,
            titleSmall,
            bodyLarge,
            bodyMedium,
            bodySmall,
            labelLarge,
            labelMedium,
            labelSmall,
        )
}
