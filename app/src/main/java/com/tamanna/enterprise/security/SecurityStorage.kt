package com.tamanna.enterprise.security

import android.content.Context
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class AppUser(
    val id: String,
    val username: String,
    val passwordHash: String,
    val role: String
)

object SecurityStorage {
    const val ROLE_ADMIN = "ADMIN"
    const val ROLE_PARTNER = "PARTNER"
    const val ROLE_VIEWER = "VIEWER"

    private const val PREFS = "tamanna_enterprise_security"
    private const val KEY_USERS = "users"
    private const val KEY_LOGIN_ENABLED = "login_enabled"
    private const val KEY_CURRENT_USER = "current_user"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getUsers(context: Context): List<AppUser> {
        val raw = prefs(context).getString(KEY_USERS, null)
        if (raw.isNullOrBlank()) return listOf(defaultAdmin())
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        AppUser(
                            id = o.getString("id"),
                            username = o.getString("username"),
                            passwordHash = o.getString("passwordHash"),
                            role = o.getString("role")
                        )
                    )
                }
            }
        }.getOrDefault(listOf(defaultAdmin()))
    }

    private fun defaultAdmin() =
        AppUser("default-admin", "admin", hashPassword("1234"), ROLE_ADMIN)

    private fun saveUsers(context: Context, users: List<AppUser>) {
        val array = JSONArray()
        users.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("username", it.username)
                    .put("passwordHash", it.passwordHash)
                    .put("role", it.role)
            )
        }
        prefs(context).edit().putString(KEY_USERS, array.toString()).apply()
    }

    fun ensureInitialized(context: Context) {
        if (prefs(context).getString(KEY_USERS, null).isNullOrBlank()) {
            saveUsers(context, listOf(defaultAdmin()))
        }
    }

    fun addUser(context: Context, username: String, password: String, role: String): Boolean {
        val cleanName = username.trim()
        if (cleanName.isBlank() || password.isBlank()) return false
        val users = getUsers(context).toMutableList()
        if (users.any { it.username.equals(cleanName, ignoreCase = true) }) return false
        users.add(AppUser(UUID.randomUUID().toString(), cleanName, hashPassword(password), role))
        saveUsers(context, users)
        return true
    }

    fun deleteUser(context: Context, id: String): Boolean {
        val users = getUsers(context)
        if (users.count { it.role == ROLE_ADMIN } <= 1 && users.any { it.id == id && it.role == ROLE_ADMIN }) {
            return false
        }
        saveUsers(context, users.filterNot { it.id == id })
        return true
    }

    fun verifyLogin(context: Context, username: String, password: String): AppUser? {
        val hash = hashPassword(password)
        return getUsers(context).firstOrNull {
            it.username.equals(username.trim(), ignoreCase = true) && it.passwordHash == hash
        }
    }

    fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun isLoginEnabled(context: Context) = prefs(context).getBoolean(KEY_LOGIN_ENABLED, false)

    fun setLoginEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGIN_ENABLED, enabled).apply()
        if (!enabled) logout(context)
    }

    fun login(context: Context, user: AppUser) {
        prefs(context).edit().putString(KEY_CURRENT_USER, user.id).apply()
    }

    fun logout(context: Context) {
        prefs(context).edit().remove(KEY_CURRENT_USER).apply()
    }

    fun getCurrentUser(context: Context): AppUser? {
        val id = prefs(context).getString(KEY_CURRENT_USER, null) ?: return null
        return getUsers(context).firstOrNull { it.id == id }
    }

    fun isLoggedIn(context: Context) = getCurrentUser(context) != null

    fun roleLabel(role: String): String = when (role) {
        ROLE_ADMIN -> "অ্যাডমিন"
        ROLE_PARTNER -> "পার্টনার"
        ROLE_VIEWER -> "ভিউয়ার"
        else -> role
    }
}
