package com.tamanna.enterprise.security

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class CloudAccessUser(
    val uid: String,
    val email: String,
    val role: String,
    val approved: Boolean
)

object CloudAccessManager {
    private const val USERS = "accessUsers"
    private const val CONFIG = "appConfig"
    private const val ADMIN = "admin"

    private fun db() = FirebaseFirestore.getInstance()
    private fun auth() = FirebaseAuth.getInstance()

    const val MASTER_REQUIRED = "MASTER_REQUIRED"

    fun hasApprovedAdmin(onResult: (Boolean) -> Unit) {
        db().collection(USERS)
            .whereEqualTo("role", SecurityStorage.ROLE_ADMIN)
            .whereEqualTo("approved", true)
            .limit(1)
            .get()
            .addOnSuccessListener { onResult(!it.isEmpty) }
            .addOnFailureListener { onResult(false) }
    }

    fun resolveGoogleLogin(
        context: Context,
        email: String,
        masterPassword: String,
        onResult: (Boolean, String, CloudAccessUser?) -> Unit
    ) {
        val firebaseUser = auth().currentUser
        if (firebaseUser == null || email.isBlank()) {
            onResult(false, "Google authentication সম্পন্ন হয়নি।", null)
            return
        }

        val uid = firebaseUser.uid
        val ref = db().collection(USERS).document(uid)

        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val role = snapshot.getString("role").orEmpty()
                val approved = snapshot.getBoolean("approved") == true
                val cloudUser = CloudAccessUser(uid, email.trim(), role, approved)

                if (approved && (role == SecurityStorage.ROLE_ADMIN || role == SecurityStorage.ROLE_PARTNER)) {
                    cacheAndLogin(context, cloudUser)
                    onResult(true, "", cloudUser)
                } else {
                    auth().signOut()
                    val message = if (role == SecurityStorage.ROLE_PARTNER) {
                        "এই Google অ্যাকাউন্টটি এখনো Admin অনুমোদন করেননি। অনুমোদনের পর লগইন করতে পারবেন।"
                    } else {
                        "এই Google অ্যাকাউন্টের অ্যাক্সেস অনুমোদিত নয়।"
                    }
                    onResult(false, message, null)
                }
                return@addOnSuccessListener
            }

            db().collection(CONFIG).document(ADMIN).get()
                .addOnSuccessListener { adminSnapshot ->
                    val adminExists = adminSnapshot.exists()
                    if (!adminExists) {
                        if (SecurityStorage.isMasterPassword(masterPassword)) {
                            bootstrapFirstAdmin(context, uid, email.trim(), onResult)
                        } else {
                            onResult(false, MASTER_REQUIRED, null)
                        }
                    } else {
                        createPendingPartner(context, uid, email.trim(), onResult)
                    }
                }
                .addOnFailureListener {
                    auth().signOut()
                    onResult(false, it.localizedMessage ?: "Admin configuration যাচাই করা যায়নি।", null)
                }
        }.addOnFailureListener {
            auth().signOut()
            onResult(false, it.localizedMessage ?: "অ্যাক্সেস যাচাই করা যায়নি।", null)
        }
    }

    fun bootstrapAfterMaster(
        context: Context,
        masterPassword: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = auth().currentUser
        val email = user?.email.orEmpty()
        val uid = user?.uid.orEmpty()
        if (uid.isBlank() || email.isBlank()) {
            onResult(false, "Google authentication পাওয়া যায়নি।")
            return
        }
        if (!SecurityStorage.isMasterPassword(masterPassword)) {
            onResult(false, "Master Password সঠিক নয়।")
            return
        }

        db().collection(CONFIG).document(ADMIN).get()
            .addOnSuccessListener { existing ->
                if (existing.exists()) {
                    onResult(false, "Admin ইতোমধ্যে সেটআপ করা আছে।")
                } else {
                    bootstrapFirstAdmin(context, uid, email, ) { ok, message, _ ->
                        if (ok) onResult(true, "প্রথম Admin সফলভাবে অনুমোদিত হয়েছে।")
                        else onResult(false, message)
                    }
                }
            }
            .addOnFailureListener {
                onResult(false, it.localizedMessage ?: "Admin configuration যাচাই করা যায়নি।")
            }
    }

    private fun bootstrapFirstAdmin(
        context: Context,
        uid: String,
        email: String,
        onResult: (Boolean, String, CloudAccessUser?) -> Unit
    ) {
        val access = mapOf(
            "uid" to uid,
            "email" to email,
            "role" to SecurityStorage.ROLE_ADMIN,
            "approved" to true,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db().collection(USERS).document(uid).set(access, SetOptions.merge())
            .addOnSuccessListener {
                db().collection(CONFIG).document(ADMIN).set(
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "role" to SecurityStorage.ROLE_ADMIN,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).addOnSuccessListener {
                    val user = CloudAccessUser(uid, email, SecurityStorage.ROLE_ADMIN, true)
                    cacheAndLogin(context, user)
                    onResult(true, "", user)
                }.addOnFailureListener {
                    auth().signOut()
                    onResult(false, "Admin নিরাপত্তা রেকর্ড সংরক্ষণ করা যায়নি।", null)
                }
            }
            .addOnFailureListener {
                auth().signOut()
                onResult(false, it.localizedMessage ?: "প্রথম Admin সেটআপ ব্যর্থ হয়েছে।", null)
            }
    }

    private fun createPendingPartner(
        context: Context,
        uid: String,
        email: String,
        onResult: (Boolean, String, CloudAccessUser?) -> Unit
    ) {
        val access = mapOf(
            "uid" to uid,
            "email" to email,
            "role" to SecurityStorage.ROLE_PARTNER,
            "approved" to false,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db().collection(USERS).document(uid).set(access, SetOptions.merge())
            .addOnSuccessListener {
                val pending = CloudAccessUser(uid, email, SecurityStorage.ROLE_PARTNER, false)
                SecurityStorage.upsertGoogleUser(context, email, SecurityStorage.ROLE_PARTNER, false, uid)
                auth().signOut()
                onResult(false, "আপনার Google অ্যাকাউন্ট Partner হিসেবে অনুমোদনের অপেক্ষায় আছে। Admin অনুমোদন করার পর লগইন করতে পারবেন।", pending)
            }
            .addOnFailureListener {
                auth().signOut()
                onResult(false, it.localizedMessage ?: "Partner approval request সংরক্ষণ করা যায়নি।", null)
            }
    }

    fun listUsers(onResult: (List<CloudAccessUser>, String?) -> Unit) {
        db().collection(USERS)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { d ->
                    val uid = d.id
                    val email = d.getString("email").orEmpty()
                    val role = d.getString("role").orEmpty()
                    if (email.isBlank() || role.isBlank()) null
                    else CloudAccessUser(uid, email, role, d.getBoolean("approved") == true)
                }
                onResult(users.sortedWith(compareBy<CloudAccessUser> { it.approved }.thenBy { it.email.lowercase() }), null)
            }
            .addOnFailureListener { onResult(emptyList(), it.localizedMessage ?: "ইউজার তালিকা আনা যায়নি।") }
    }

    fun approvePartner(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        val admin = auth().currentUser
        if (admin == null) {
            onResult(false, "Admin authentication পাওয়া যায়নি।")
            return
        }

        db().collection(USERS).document(uid)
            .update(
                mapOf(
                    "role" to SecurityStorage.ROLE_PARTNER,
                    "approved" to true,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                listUsers { users, _ ->
                    users.firstOrNull { it.uid == uid }?.let {
                        SecurityStorage.upsertGoogleUser(context, it.email, it.role, true, it.uid)
                    }
                }
                onResult(true, "Partner অনুমোদন হয়েছে।")
            }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "Partner অনুমোদন করা যায়নি।") }
    }

    fun revokeUser(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        db().collection(USERS).document(uid)
            .update(
                mapOf(
                    "approved" to false,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                listUsers { users, _ ->
                    users.firstOrNull { it.uid == uid }?.let {
                        SecurityStorage.upsertGoogleUser(context, it.email, it.role, false, it.uid)
                    }
                }
                onResult(true, "অ্যাক্সেস বাতিল হয়েছে।")
            }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "অ্যাক্সেস বাতিল করা যায়নি।") }
    }

    private fun cacheAndLogin(context: Context, user: CloudAccessUser) {
        SecurityStorage.upsertGoogleUser(context, user.email, user.role, user.approved, user.uid)
        val local = SecurityStorage.findByGoogleEmail(context, user.email)
        if (local != null) SecurityStorage.login(context, local)
    }
}
