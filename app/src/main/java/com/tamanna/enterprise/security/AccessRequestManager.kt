package com.tamanna.enterprise.security

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import android.content.Context
import com.google.firebase.FirebaseApp

data class AccessRecord(val uid: String, val email: String, val status: String, val startAt: Long = 0L, val expiresAt: Long = 0L, val notificationsEnabled: Boolean = false)

object AccessRequestManager {
    private const val COLLECTION = "accessRequests"
    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_BLOCKED = "BLOCKED"
    const val STATUS_REJECTED = "REJECTED"
    const val STATUS_EXPIRED = "EXPIRED"

    private const val PREFS = "tamanna_access_cache"
    private const val KEY_UID = "approved_uid"
    private const val KEY_EMAIL = "approved_email"
    private const val KEY_EXPIRES_AT = "approved_expires_at"

    private fun cacheApproved(uid: String, email: String, expiresAt: Long) {
        val context = FirebaseApp.getInstance().applicationContext
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_UID, uid)
            .putString(KEY_EMAIL, email.trim().lowercase())
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun clearCachedApproval(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun hasCachedApproval(uid: String, email: String): Boolean {
        val context = FirebaseApp.getInstance().applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cachedUid = prefs.getString(KEY_UID, "").orEmpty()
        val cachedEmail = prefs.getString(KEY_EMAIL, "").orEmpty()
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return cachedUid == uid && cachedEmail.equals(email.trim(), ignoreCase = true) &&
            (expiresAt <= 0L || expiresAt > System.currentTimeMillis())
    }

    fun requestOrCheck(onResult: (Boolean, String, AccessRecord?) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        val uid = user?.uid.orEmpty()
        val email = user?.email?.trim().orEmpty()
        if (uid.isBlank() || email.isBlank()) { onResult(false, "Google account verification সম্পন্ন হয়নি।", null); return }
        val ref = FirebaseFirestore.getInstance().collection(COLLECTION).document(uid)
        ref.get(Source.SERVER).addOnSuccessListener { doc ->
            if (!doc.exists()) {
                val data = hashMapOf("uid" to uid, "email" to email, "status" to STATUS_PENDING, "requestedAt" to System.currentTimeMillis(), "startAt" to 0L, "expiresAt" to 0L, "notificationsEnabled" to false)
                ref.set(data).addOnSuccessListener { onResult(false, "আপনার Gmail অনুমোদনের জন্য Admin-এর কাছে পাঠানো হয়েছে।", AccessRecord(uid, email, STATUS_PENDING)) }
                    .addOnFailureListener { onResult(false, "অনুমোদনের অনুরোধ পাঠানো যায়নি: ${it.localizedMessage ?: "আবার চেষ্টা করুন।"}", null) }
                return@addOnSuccessListener
            }
            val status = doc.getString("status").orEmpty().ifBlank { STATUS_PENDING }
            val startAt = doc.getLong("startAt") ?: 0L
            val expiresAt = doc.getLong("expiresAt") ?: 0L
            val notifications = doc.getBoolean("notificationsEnabled") == true
            val now = System.currentTimeMillis()
            if (status == STATUS_ACTIVE && expiresAt > 0L && expiresAt <= now) { onResult(false, "আপনার অনুমোদনের মেয়াদ শেষ হয়েছে। Admin-এর কাছ থেকে Extend/Reactivate করতে হবে।", AccessRecord(uid, email, STATUS_EXPIRED, startAt, expiresAt, notifications)); return@addOnSuccessListener }
            if (status == STATUS_ACTIVE && (expiresAt <= 0L || expiresAt > now)) cacheApproved(uid, email, expiresAt) else clearCachedApproval(FirebaseApp.getInstance().applicationContext)
            val message = when (status) {
                STATUS_PENDING -> "আপনার Gmail অনুমোদনের অপেক্ষায় আছে।"
                STATUS_REJECTED -> "Admin আপনার অনুরোধটি অনুমোদন করেননি।"
                STATUS_BLOCKED -> "আপনার access Admin Block করেছেন।"
                STATUS_EXPIRED -> "আপনার অনুমোদনের মেয়াদ শেষ হয়েছে।"
                STATUS_ACTIVE -> ""
                else -> "আপনার access status: $status"
            }
            onResult(status == STATUS_ACTIVE && (expiresAt <= 0L || expiresAt > now), message, AccessRecord(uid, email, status, startAt, expiresAt, notifications))
        }.addOnFailureListener {
            // Offline access is limited to a previously approved matching Firebase UID and Gmail.
            if (hasCachedApproval(uid, email)) {
                onResult(true, "অফলাইন মোড: আগের অনুমোদিত Gmail ব্যবহার করা হচ্ছে।", AccessRecord(uid, email, STATUS_ACTIVE))
            } else {
                onResult(false, "Server থেকে access status যাচাই করা যায়নি। প্রথমবার অনুমোদনের জন্য Internet প্রয়োজন।", null)
            }
        }
    }
}