package dev.izumi.appopsnext.settings

enum class AppLanguage(
    val languageTags: String,
) {
    SYSTEM(""),
    SIMPLIFIED_CHINESE("zh-Hans"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromLanguageTags(languageTags: String): AppLanguage {
            if (languageTags.isBlank()) return SYSTEM
            val primaryTag = languageTags
                .substringBefore(',')
                .trim()
                .lowercase()
            return when {
                primaryTag.startsWith("zh") -> SIMPLIFIED_CHINESE
                primaryTag.startsWith("en") -> ENGLISH
                else -> SYSTEM
            }
        }
    }
}
