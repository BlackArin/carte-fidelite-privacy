package com.cartefidelite

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

object LocaleHelper {
    val languages = listOf(
        "fr" to "Français",
        "en" to "English",
        "es" to "Español",
        "de" to "Deutsch",
        "it" to "Italiano",
        "pt" to "Português",
        "pl" to "Polski"
    )

    private const val PREFS = "settings"
    private const val KEY = "language"

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (saved != null) return saved
        val system = Locale.getDefault().language
        return if (languages.any { it.first == system }) system else "en"
    }

    fun save(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, code) }
    }

    fun wrap(base: Context): Context {
        val saved = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(saved)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
