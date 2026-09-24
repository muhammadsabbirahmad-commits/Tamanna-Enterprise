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
    val role: String,
    val approved: Boolean = true,
    val googleEmail: String = ""
)

object SecurityStorage {
    const val ROLE_ADMIN = "ADMIN"
    const val ROLE_PARTNER = "PARTNER"
    const val ROLE_VIEWER = "VIEWER"

    private const val PREFS = "tamanna_enterprise_security"
    private const val KEY_USERS = "users"
    private const val KEY_LOGIN_ENABLED = "login_enabled"
    private const val KEY_CURRENT_USER = "current_user"

    private const val MASTER_PASSWORD = "##Sabbir123ahmad@@"

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
                            role = o.getString("role"),
                            approved = o.optBoolean("approved", true),
                            googleEmail = o.optString("googleEmail")
                        )
                    )
                }
            }
        }.getOrDefault(listOf(defaultAdmin()))
    }

    // ডিফল্ট অ্যাডমিনের কোনো পাসওয়ার্ড নেই (ফাঁকা)
    private fun defaultAdmin() =
        AppUser("default-admin", "admin", "", ROLE_ADMIN, true, "")

    private fun saveUsers(context: Context, users: List<AppUser>) {
        val array = JSONArray()
        users.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("username", it.username)
                    .put("passwordHash", it.passwordHash)
                    .put("role", it.role)
                    .put("approved", it.approved)
                    .put("googleEmail", it.googleEmail)
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
        users.add(AppUser(UUID.randomUUID().toString(), cleanName, hashPassword(password), role, role != ROLE_PARTNER, ""))
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
            it.username.equals(username.trim(), ignoreCase = true) &&
                it.passwordHash == hash && it.approved
        }
    }

    fun isMasterPassword(password: String): Boolean = password == MASTER_PASSWORD

    fun findByGoogleEmail(context: Context, email: String): AppUser? =
        getUsers(context).firstOrNull { it.googleEmail.equals(email.trim(), ignoreCase = true) }

    fun upsertGoogleUser(context: Context, email: String, role: String, approved: Boolean, uid: String): AppUser {
        val clean = email.trim()
        val users = getUsers(context).toMutableList()
        val existingIndex = users.indexOfFirst { it.googleEmail.equals(clean, ignoreCase = true) || it.id == uid }
        val user = AppUser(id = uid.ifBlank { UUID.randomUUID().toString() }, username = clean, passwordHash = "", role = role, approved = approved, googleEmail = clean)
        if (existingIndex >= 0) users[existingIndex] = user else users.add(user)
        saveUsers(context, users)
        return user
    }

    fun hashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun isLoginEnabled(context: Context) = prefs(context).getBoolean(KEY_LOGIN_ENABLED, false)
    
    fun setLoginEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGIN_ENABLED, enabled).apply()
        if (!enabled) logout(context)
    }

    fun login(context: Context, user: AppUser) { prefs(context).edit().putString(KEY_CURRENT_USER, user.id).apply() }
    fun logout(context: Context) { prefs(context).edit().remove(KEY_CURRENT_USER).apply() }
    fun getCurrentUser(context: Context): AppUser? {
        val id = prefs(context).getString(KEY_CURRENT_USER, null) ?: return null
        return getUsers(context).firstOrNull { it.id == id }
    }

    fun isLoggedIn(context: Context) = getCurrentUser(context) != null
    fun canWrite(context: Context): Boolean = getCurrentUser(context)?.role == ROLE_ADMIN
    fun canManage(context: Context): Boolean = getCurrentUser(context)?.role == ROLE_ADMIN

    // --- নতুন পিন (PIN) সিস্টেম লজিক ---
    
    // চেক করবে ইউজারের কোনো পিন সেট করা আছে কি না
    fun isAdminPinSet(context: Context): Boolean {
        val admin = getUsers(context).firstOrNull { it.role == ROLE_ADMIN }
        return admin != null && admin.passwordHash.isNotEmpty()
    }

    // ডিলিট করার সময় পিন সঠিক কি না তা যাচাই করবে
    fun verifyAdminPin(context: Context, pin: String): Boolean {
        val admin = getUsers(context).firstOrNull { it.role == ROLE_ADMIN } ?: return false
        if (admin.passwordHash.isEmpty()) return true // পিন সেট না থাকলে অটোমেটিক True
        return admin.passwordHash == hashPassword(pin)
    }

    // নতুন পিন সেট বা বন্ধ করার লজিক
    fun changeAdminPin(context: Context, currentPin: String, newPin: String): Boolean {
        val users = getUsers(context).toMutableList()
        val adminIndex = users.indexOfFirst { it.role == ROLE_ADMIN }
        if (adminIndex < 0) return false
        val admin = users[adminIndex]

        // যদি আগে থেকে পিন থাকে, তবে বর্তমান পিনটি মেলাতে হবে
        if (admin.passwordHash.isNotEmpty() && admin.passwordHash != hashPassword(currentPin)) {
            return false
        }

        // নতুন পিন ফাঁকা দিলে পিন সিস্টেম বন্ধ হয়ে যাবে (Empty Hash)
        val newHash = if (newPin.isNotBlank()) hashPassword(newPin) else ""
        users[adminIndex] = admin.copy(passwordHash = newHash)
        saveUsers(context, users)
        return true
    }
}
