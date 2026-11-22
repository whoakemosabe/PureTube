package com.hhst.youtubelite.core

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.core.os.LocaleListCompat

/**
 * API 33+ delegates to the system's per-app locales; older versions wrap activity contexts
 * with the persisted BCP-47 tag. An empty tag always means "follow the system".
 */
object AppLocale {
    /** Puretube ships English only; kept so a language saved by an older build still resolves. */
    val OPTIONS: List<Pair<String, String>> = listOf(
        "" to "System default",
        "en" to "English",
    )

    fun tag(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
        } else AppSettings.language(context)

    /** Applies the persisted tag; true when the system-level path was used. */
    fun apply(context: Context, tag: String): Boolean {
        AppSettings.setLanguage(context, tag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return false
            manager.applicationLocales =
                if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return true
        }
        return false
    }

    /** Wraps a base context with the persisted locale on API levels the OS does not manage. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = AppSettings.language(base)
        if (tag.isBlank()) return base
        val locales = LocaleListCompat.forLanguageTags(tag)
        if (locales.isEmpty) return base
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocales(locales.toLanguageTags().let(LocaleList::forLanguageTags))
        return base.createConfigurationContext(config)
    }
}

/** Boot-time synchronous reads for values needed before DI is up. */
internal object AppSettings {
    private const val KEY_LANGUAGE = "preferences:app_language"

    /** Same prefs file the extension settings store writes; safe before Koin starts. */
    fun language(context: Context): String =
        PreferenceStore.app(context).readString(KEY_LANGUAGE).orEmpty()

    fun setLanguage(context: Context, tag: String) {
        PreferenceStore.app(context).putString(KEY_LANGUAGE, tag)
    }
}
