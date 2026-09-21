package com.tamanna.enterprise.business

import android.content.Context

/**
 * Provides Business-scoped SharedPreferences without losing existing customer data.
 *
 * Until a real Business Account is selected, the app uses "legacy-business".
 * On first access for that business, the old unscoped preference file is copied
 * into the business-scoped file. Existing data therefore remains available.
 */
object BusinessStorage {
    private const val META_PREFS = "tamanna_business_context"
    private const val ACTIVE_BUSINESS_ID = "active_business_id"
    const val LEGACY_BUSINESS_ID = "legacy-business"

    fun getActiveBusinessId(context: Context): String =
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE_BUSINESS_ID, LEGACY_BUSINESS_ID)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: LEGACY_BUSINESS_ID

    fun setActiveBusinessId(context: Context, businessId: String) {
        val clean = businessId.trim()
        require(clean.isNotBlank()) { "businessId cannot be blank" }
        context.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ACTIVE_BUSINESS_ID, clean)
            .apply()
    }

    fun isLegacyBusiness(context: Context): Boolean =
        getActiveBusinessId(context) == LEGACY_BUSINESS_ID

    fun prefs(context: Context, legacyPrefsName: String): android.content.SharedPreferences {
        val businessId = getActiveBusinessId(context)
        val scopedName = legacyPrefsName + "__business__" + safeId(businessId)
        val scoped = context.getSharedPreferences(scopedName, Context.MODE_PRIVATE)

        // One-time, non-destructive migration for the existing installation.
        if (businessId == LEGACY_BUSINESS_ID &&
            !scoped.getBoolean("__legacy_migrated", false)
        ) {
            val legacy = context.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
            val editor = scoped.edit()
            for ((key, value) in legacy.all) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key, value as? Set<String>)
                    }
                }
            }
            editor.putBoolean("__legacy_migrated", true).apply()
        }
        return scoped
    }

    private fun safeId(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
}
