package com.tamanna.enterprise.security

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.business.BusinessMembershipManager

data class CloudAccessUser(val uid: String, val email: String, val role: String, val approved: Boolean, val blocked: Boolean = false)

object CloudAccessManager {
    private fun auth() = FirebaseAuth.getInstance()
    private fun db() = FirebaseFirestore.getInstance()

    fun resolveGoogleLogin(context: Context, email: String, adminLogin: Boolean, onResult: (Boolean, String, CloudAccessUser?) -> Unit) {
        val u = auth().currentUser ?: run { onResult(false, "Google authentication সম্পন্ন হয়নি।", null); return }
        val verifiedEmail = u.email?.trim()?.lowercase().orEmpty()
        if (verifiedEmail.isBlank()) {
            auth().signOut()
            onResult(false, "Google account-এর verified email পাওয়া যায়নি।", null)
            return
        }

        val businessId = BusinessAccountStorage.get(context).businessId
        if (businessId.isBlank() || businessId == BusinessAccountStorage.LEGACY_BUSINESS_ID) {
            auth().signOut()
            onResult(false, "Business access এখনো প্রস্তুত হয়নি।", null)
            return
        }

        BusinessMembershipManager.currentMember(businessId) { member ->
            if (member != null) {
                when {
                    member.blocked -> { auth().signOut(); onResult(false, "অ্যাকাউন্ট Block করা হয়েছে।", null) }
                    !member.approved -> { auth().signOut(); onResult(false, "অ্যাক্সেস এখনো অনুমোদন করা হয়নি।", null) }
                    member.role != "OWNER" -> { auth().signOut(); onResult(false, "এই Gmail Business Owner নয়।", null) }
                    else -> {
                        BusinessAccountStorage.setOwnerUid(context, u.uid)
                        val user = CloudAccessUser(u.uid, verifiedEmail, SecurityStorage.ROLE_ADMIN, true, false)
                        saveLocalLogin(context, user)
                        onResult(true, "", user)
                    }
                }
            } else {
                BusinessMembershipManager.createOrUpdateOwner(businessId, verifiedEmail) { ok, msg ->
                    if (!ok) { auth().signOut(); onResult(false, msg, null) }
                    else {
                        BusinessAccountStorage.setOwnerUid(context, u.uid)
                        val user = CloudAccessUser(u.uid, verifiedEmail, SecurityStorage.ROLE_ADMIN, true, false)
                        saveLocalLogin(context, user)
                        onResult(true, "", user)
                    }
                }
            }
        }
    }

    fun validateCurrentSession(context: Context, onResult: (Boolean) -> Unit) {
        val u = auth().currentUser ?: run { onResult(false); return }
        val businessId = BusinessAccountStorage.get(context).businessId
        if (businessId.isBlank() || businessId == BusinessAccountStorage.LEGACY_BUSINESS_ID) {
            SecurityStorage.logout(context)
            auth().signOut()
            onResult(false)
            return
        }

        BusinessMembershipManager.currentMember(businessId) { member ->
            if (member == null) {
                if (SecurityStorage.isLoggedIn(context)) onResult(true)
                else { SecurityStorage.logout(context); auth().signOut(); onResult(false) }
                return@currentMember
            }
            if (!member.approved || member.blocked || member.role != "OWNER") {
                SecurityStorage.logout(context)
                auth().signOut()
                onResult(false)
                return@currentMember
            }
            val cached = SecurityStorage.upsertGoogleUser(
                context,
                member.email.ifBlank { u.email.orEmpty() },
                SecurityStorage.ROLE_ADMIN,
                true,
                u.uid
            )
            SecurityStorage.login(context, cached)
            onResult(true)
        }
    }

    private fun saveLocalLogin(context: Context, user: CloudAccessUser) {
        SecurityStorage.upsertGoogleUser(context, user.email, user.role, user.approved, user.uid)
        SecurityStorage.findByGoogleEmail(context, user.email)?.let { SecurityStorage.login(context, it) }
    }
}
