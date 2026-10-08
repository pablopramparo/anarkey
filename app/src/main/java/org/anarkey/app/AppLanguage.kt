package org.anarkey.app

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.app.LocaleManager
import java.util.Locale

internal object AppLanguage {
    const val SYSTEM = "system"
    private const val PREFS = "app_language"
    private const val KEY = "language_tag"

    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val tags = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            if (tags.isBlank()) return SYSTEM
            return tags.substringBefore(',').substringBefore('-').lowercase(Locale.ROOT)
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: SYSTEM
    }

    fun set(context: Context, language: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = if (language == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (language == SYSTEM) remove(KEY) else putString(KEY, language)
            }.apply()
        }
    }

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val language = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            setLayoutDirection(Locale.forLanguageTag(language))
        }
        return base.createConfigurationContext(configuration)
    }
}
