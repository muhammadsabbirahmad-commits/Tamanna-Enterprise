package com.tamanna.enterprise.business

import android.content.Context

enum class LicenseStatus { PENDING, ACTIVE, EXPIRED, BLOCKED }

data class LicenseInfo(
    val code: String,
    val businessId: String = "",
    val businessName: String = "",
    val type: String = "CUSTOM",
    val status: String = LicenseStatus.PENDING.name,
    val expiresAt: Long = 0L,
    val deviceLimit: Int = 1
)

object LicenseStorage {
    private const val PREFS = "tamanna_license"
    private const val KEY_CODE = "license_code"
    private const val KEY_BUSINESS_ID = "business_id"
    private const val KEY_BUSINESS_NAME = "business_name"
    private const val KEY_TYPE = "license_type"
    private const val KEY_STATUS = "license_status"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_DEVICE_LIMIT = "device_limit"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): LicenseInfo = LicenseInfo(
        code = prefs(context).getString(KEY_CODE, "").orEmpty(),
        businessId = prefs(context).getString(KEY_BUSINESS_ID, "").orEmpty(),
        businessName = prefs(context).getString(KEY_BUSINESS_NAME, "").orEmpty(),
        type = prefs(context).getString(KEY_TYPE, "CUSTOM").orEmpty(),
        status = prefs(context).getString(KEY_STATUS, LicenseStatus.PENDING.name).orEmpty(),
        expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L),
        deviceLimit = prefs(context).getInt(KEY_DEVICE_LIMIT, 1)
    )

    fun save(context: Context, info: LicenseInfo) {
        prefs(context).edit()
            .putString(KEY_CODE, info.code.trim())
            .putString(KEY_BUSINESS_ID, info.businessId.trim())
            .putString(KEY_BUSINESS_NAME, info.businessName.trim())
            .putString(KEY_TYPE, info.type.trim().uppercase())
            .putString(KEY_STATUS, info.status.trim().uppercase())
            .putLong(KEY_EXPIRES_AT, info.expiresAt)
            .putInt(KEY_DEVICE_LIMIT, info.deviceLimit.coerceAtLeast(1))
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun isActive(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val info = get(context)
        if (info.code.isBlank() || info.businessId.isBlank()) return false
        if (info.status != LicenseStatus.ACTIVE.name) return false
        return info.expiresAt <= 0L || info.expiresAt > now
    }
}
