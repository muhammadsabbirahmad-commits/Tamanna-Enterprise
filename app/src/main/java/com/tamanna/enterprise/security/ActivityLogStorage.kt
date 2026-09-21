package com.tamanna.enterprise.security

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActivityLogEntry(
    val id: Long,
    val date: String,
    val username: String,
    val action: String,
    val details: String
)

object ActivityLogStorage {
    private const val PREFS = "tamanna_enterprise_activity_log"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 500

    fun add(context: Context, action: String, details: String) {
        val user = SecurityStorage.getCurrentUser(context)?.username ?: "system"
        val list = get(context).toMutableList()
        list.add(
            ActivityLogEntry(
                System.currentTimeMillis(),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                user, action, details
            )
        )
        val array = JSONArray()
        list.takeLast(MAX_LOGS).forEach {
            array.put(JSONObject().apply {
                put("id", it.id); put("date", it.date); put("username", it.username)
                put("action", it.action); put("details", it.details)
            })
        }
        BusinessStorage.prefs(context, PREFS).edit()
            .putString(KEY_LOGS, array.toString()).apply()
    }

    fun get(context: Context): List<ActivityLogEntry> {
        val raw = BusinessStorage.prefs(context, PREFS)
            .getString(KEY_LOGS, "[]") ?: "[]"
        return runCatching {
            val a = JSONArray(raw)
            buildList {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    add(ActivityLogEntry(
                        o.optLong("id"), o.optString("date"), o.optString("username"),
                        o.optString("action"), o.optString("details")
                    ))
                }
            }.sortedByDescending { it.id }
        }.getOrDefault(emptyList())
    }
}
