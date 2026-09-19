package com.tamanna.enterprise.settings

import android.content.Context

object ThemeStorage {
    private const val PREFS = "tamanna_enterprise_settings"
    private const val KEY_THEME = "app_theme"

    fun getTheme(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "green") ?: "green"

    fun saveTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme)
            .apply()
    }
}
