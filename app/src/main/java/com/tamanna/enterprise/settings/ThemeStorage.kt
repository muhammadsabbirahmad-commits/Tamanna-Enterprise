package com.tamanna.enterprise.settings

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage

object ThemeStorage {
    private const val PREFS = "tamanna_enterprise_settings"
    private const val KEY_THEME = "app_theme"

    private fun prefs(context: Context) = BusinessStorage.prefs(context, PREFS)

    fun getTheme(context: Context): String {
        val scoped = prefs(context)
        if (!scoped.contains(KEY_THEME)) {
            // Preserve the theme selected by existing installations while moving
            // this setting into the business-scoped namespace used by Firestore sync.
            val legacyTheme = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, null)
            if (!legacyTheme.isNullOrBlank()) {
                scoped.edit().putString(KEY_THEME, legacyTheme).apply()
            }
        }
        return scoped.getString(KEY_THEME, "green") ?: "green"
    }

    fun saveTheme(context: Context, theme: String) {
        prefs(context).edit().putString(KEY_THEME, theme).apply()
    }
}
