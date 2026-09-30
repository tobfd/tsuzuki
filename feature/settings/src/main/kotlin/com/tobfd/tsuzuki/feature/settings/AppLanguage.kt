package com.tobfd.tsuzuki.feature.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** The app languages (docs/PRODUCT.md, D5, and `res/xml/locales_config.xml` in `app`). */
enum class AppLanguage(val tag: String?) {
    System(null),
    English("en"),
    German("de")
}

/** The per-app language; Android recreates the activity when it changes. */
interface AppLanguageController {
    val current: AppLanguage

    fun set(language: AppLanguage)
}

/** Through the platform's `LocaleManager`, which exists from Android 13. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LocaleManagerLanguageController(context: Context) : AppLanguageController {
    private val localeManager = context.getSystemService(LocaleManager::class.java)

    override val current: AppLanguage
        get() {
            val locales = localeManager.applicationLocales
            if (locales.isEmpty) return AppLanguage.System
            val language = locales[0].language
            return AppLanguage.entries.firstOrNull { it.tag == language } ?: AppLanguage.System
        }

    override fun set(language: AppLanguage) {
        localeManager.applicationLocales = language.tag?.let { LocaleList.forLanguageTags(it) }
            ?: LocaleList.getEmptyLocaleList()
    }
}

/** Null on Android 12, which has no per-app languages: the setting is hidden there. */
@Composable
internal fun rememberAppLanguageController(): AppLanguageController? {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LocaleManagerLanguageController(context) else null
    }
}
