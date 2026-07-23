package dev.izumi.appopsnext.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

class ApplicationLanguageManager(
    context: Context,
) {
    private val localeManager =
        context.getSystemService(LocaleManager::class.java)

    fun currentLanguage(): AppLanguage =
        AppLanguage.fromLanguageTags(
            localeManager.applicationLocales.toLanguageTags(),
        )

    fun setLanguage(language: AppLanguage) {
        localeManager.applicationLocales =
            LocaleList.forLanguageTags(language.languageTags)
    }
}
