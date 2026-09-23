package com.tamanna.enterprise.security

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

data class AccessRecord(val uid: String, val email: String, val status: String, val startAt: Long = 0L, val expiresAt: Long = 0L, val notificationsEnabled: Boolean = false)

object AccessRequestManager {
    private const val COLLECTION = "accessRequests"
    const val STATUS_PENDING = "PENDING"
    const val STATUS_ACTIVE = "ACTIVE"
    const val STATUS_BLOCKED = "BLOCKED"
    const val STATUS_REJECTED = "REJECTED"
    const val STATUS_EXPIRED = "EXPIRED"

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
            val message = when (status) {
                STATUS_PENDING -> "আপনার Gmail অনুমোদনের অপেক্ষায় আছে।"
                STATUS_REJECTED -> "Admin আপনার অনুরোধটি অনুমোদন করেননি।"
                STATUS_BLOCKED -> "আপনার access Admin Block করেছেন।"
                STATUS_EXPIRED -> "আপনার অনুমোদনের মেয়াদ শেষ হয়েছে।"
                STATUS_ACTIVE -> ""
                else -> "আপনার access status: $status"
            }
            onResult(status == STATUS_ACTIVE && (expiresAt <= 0L || expiresAt > now), message, AccessRecord(uid, email, status, startAt, expiresAt, notifications))
        }.addOnFailureListener { onResult(false, "Server থেকে access status যাচাই করা যায়নি: ${it.localizedMessage ?: "Internet/Firestore Rules পরীক্ষা করুন।"}", null) }
    }
}