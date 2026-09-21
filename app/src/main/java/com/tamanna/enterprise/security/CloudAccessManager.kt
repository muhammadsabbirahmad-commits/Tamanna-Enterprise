package com.tamanna.enterprise.security

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class CloudAccessUser(
    val uid: String,
    val email: String,
    val role: String,
    val approved: Boolean,
    val blocked: Boolean = false
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
        adminLogin: Boolean,
        onResult: (Boolean, String, CloudAccessUser?) -> Unit
    ) {
        val firebaseUser = auth().currentUser
        if (firebaseUser == null || email.isBlank()) {
            onResult(false, "Google authentication সম্পন্ন হয়নি।", null)
            return
        }

        val uid = firebaseUser.uid
        val normalizedEmail = email.trim()

        // Admin flow: after Google authentication succeeds, always open the
        // Master Password step before doing any access lookup. This prevents
        // the Firestore permission/error message from replacing the password
        // screen during the first Admin setup.
        if (adminLogin && masterPassword.isBlank()) {
            onResult(false, MASTER_REQUIRED, null)
            return
        }

        // The singleton Admin record is the source of truth for whether the
        // first Admin has already been created. This read is intentionally
        // independent of the user's access record so the first Admin can
        // bootstrap without hitting an accessUsers permission check first.
        db().collection(CONFIG).document(ADMIN).get()
            .addOnSuccessListener { adminSnapshot ->
                val adminExists = adminSnapshot.exists()

                if (adminLogin && !adminExists) {
                    if (SecurityStorage.isMasterPassword(masterPassword)) {
                        bootstrapFirstAdmin(context, uid, normalizedEmail, onResult)
                    } else {
                        onResult(false, MASTER_REQUIRED, null)
                    }
                    return@addOnSuccessListener
                }

                // For an existing Admin, or for Partner requests after Admin
                // bootstrap, read only the signed-in user's own access record.
                db().collection(USERS).document(uid).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            val role = snapshot.getString("role").orEmpty()
                            val approved = snapshot.getBoolean("approved") == true
                            val blocked = snapshot.getBoolean("blocked") == true
                            val cloudUser = CloudAccessUser(uid, normalizedEmail, role, approved, blocked)

                            if (blocked && role == SecurityStorage.ROLE_PARTNER) {
                                auth().signOut()
                                onResult(false, "এই Partner account সাময়িকভাবে Block করা হয়েছে। Admin-এর অনুমতি ছাড়া প্রবেশ করা যাবে না।", null)
                                return@addOnSuccessListener
                            }

                            if (approved && role == SecurityStorage.ROLE_ADMIN) {
                                if (adminLogin) {
                                    cacheAndLogin(context, cloudUser)
                                    onResult(true, "", cloudUser)
                                } else {
                                    auth().signOut()
                                    onResult(false, "এই Gmail ইতিমধ্যে Admin হিসেবে নিবন্ধিত। Partner Login-এর জন্য অন্য Gmail ব্যবহার করুন।", null)
                                }
                                return@addOnSuccessListener
                            }

                            if (approved && role == SecurityStorage.ROLE_PARTNER) {
                                if (!adminLogin) {
                                    cacheAndLogin(context, cloudUser)
                                    onResult(true, "", cloudUser)
                                } else {
                                    auth().signOut()
                                    onResult(false, "এই Gmail Partner account হিসেবে অনুমোদিত। Admin Login-এর জন্য Admin-এর Gmail ব্যবহার করুন।", null)
                                }
                                return@addOnSuccessListener
                            }

                            if (!approved && role == SecurityStorage.ROLE_PARTNER) {
                                auth().signOut()
                                onResult(false, "আপনার Partner access এখনো Admin অনুমোদন করেননি। অনুমোদনের পর Partner Login দিয়ে প্রবেশ করুন।", null)
                                return@addOnSuccessListener
                            }

                            auth().signOut()
                            onResult(false, "এই Google account-এর জন্য অনুমোদিত access পাওয়া যায়নি।", null)
                            return@addOnSuccessListener
                        }

                        if (adminLogin) {
                            auth().signOut()
                            onResult(
                                false,
                                if (adminExists) {
                                    "Admin account ইতিমধ্যে সেটআপ করা আছে। এই Gmail Admin নয়।"
                                } else {
                                    "Admin account সেটআপ করা যায়নি। আবার চেষ্টা করুন।"
                                },
                                null
                            )
                        } else {
                            if (!adminExists) {
                                auth().signOut()
                                onResult(false, "প্রথমে Settings → Admin Login দিয়ে Admin account সেটআপ করতে হবে।", null)
                            } else {
                                createPendingPartner(context, uid, normalizedEmail, onResult)
                            }
                        }
                    }
                    .addOnFailureListener {
                        auth().signOut()
                        onResult(false, it.localizedMessage ?: "Google access record যাচাই করা যায়নি।", null)
                    }
            }
            .addOnFailureListener {
                auth().signOut()
                onResult(false, it.localizedMessage ?: "Admin configuration যাচাই করা যায়নি।", null)
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
                    bootstrapFirstAdmin(context, uid, email) { ok, message, _ ->
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
            "blocked" to false,
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
            "blocked" to false,
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

    fun validateCurrentSession(context: Context, onResult: (Boolean) -> Unit) {
        val user = auth().currentUser
        if (user == null) {
            onResult(false)
            return
        }

        db().collection(USERS).document(user.uid).get()
            .addOnSuccessListener { snapshot ->
                val role = snapshot.getString("role").orEmpty()
                val approved = snapshot.getBoolean("approved") == true
                val blocked = snapshot.getBoolean("blocked") == true
                val email = snapshot.getString("email").orEmpty().ifBlank { user.email.orEmpty() }

                if (blocked) {
                    SecurityStorage.logout(context)
                    auth().signOut()
                    onResult(false)
                    return@addOnSuccessListener
                }

                if (approved && (role == SecurityStorage.ROLE_ADMIN || role == SecurityStorage.ROLE_PARTNER)) {
                    val cached = SecurityStorage.upsertGoogleUser(
                        context,
                        email,
                        role,
                        true,
                        user.uid
                    )
                    SecurityStorage.login(context, cached)
                    onResult(true)
                } else {
                    SecurityStorage.logout(context)
                    auth().signOut()
                    onResult(false)
                }
            }
            .addOnFailureListener {
                onResult(false)
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
                    "blocked" to false,
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

    fun rejectPendingPartner(uid: String, onResult: (Boolean, String) -> Unit) {
        db().collection(USERS).document(uid)
            .delete()
            .addOnSuccessListener {
                onResult(true, "Partner আবেদন প্রত্যাখ্যান করা হয়েছে।")
            }
            .addOnFailureListener {
                onResult(false, it.localizedMessage ?: "Partner আবেদন প্রত্যাখ্যান করা যায়নি।")
            }
    }

    fun blockPartner(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        db().collection(USERS).document(uid)
            .update(
                mapOf(
                    "approved" to false,
                    "blocked" to true,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                listUsers { users, _ ->
                    users.firstOrNull { it.uid == uid }?.let {
                        SecurityStorage.upsertGoogleUser(context, it.email, it.role, false, it.uid)
                    }
                }
                onResult(true, "Partner Block করা হয়েছে।")
            }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "Partner Block করা যায়নি।") }
    }

    fun unblockPartner(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        db().collection(USERS).document(uid)
            .update(
                mapOf(
                    "approved" to true,
                    "blocked" to false,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                listUsers { users, _ ->
                    users.firstOrNull { it.uid == uid }?.let {
                        SecurityStorage.upsertGoogleUser(context, it.email, it.role, true, it.uid)
                    }
                }
                onResult(true, "Partner আবার Active হয়েছে।")
            }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "Partner Unblock করা যায়নি।") }
    }

    fun removePartner(uid: String, onResult: (Boolean, String) -> Unit) {
        db().collection(USERS).document(uid)
            .delete()
            .addOnSuccessListener { onResult(true, "Partner তালিকা থেকে Remove করা হয়েছে।") }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "Partner Remove করা যায়নি।") }
    }

    private fun cacheAndLogin(context: Context, user: CloudAccessUser) {
        SecurityStorage.upsertGoogleUser(context, user.email, user.role, user.approved, user.uid)
        val local = SecurityStorage.findByGoogleEmail(context, user.email)
        if (local != null) SecurityStorage.login(context, local)
    }
}
