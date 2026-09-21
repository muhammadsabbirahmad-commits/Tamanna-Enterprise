package com.tamanna.enterprise.security

import android.content.Context
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.business.BusinessMembershipManager
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

    fun hasApprovedAdmin(onResult: (Boolean) -> Unit) { onResult(false) }

    fun resolveGoogleLogin(context: Context, email: String, masterPassword: String, adminLogin: Boolean, onResult: (Boolean, String, CloudAccessUser?) -> Unit) {
        val firebaseUser = auth().currentUser
        if (firebaseUser == null || email.isBlank()) {
            onResult(false, "Google authentication সম্পন্ন হয়নি।", null)
            return
        }
        val business = BusinessAccountStorage.get(context)
        val license = com.tamanna.enterprise.business.LicenseStorage.get(context)
        if (business.businessId.isBlank() || business.businessId == BusinessAccountStorage.LEGACY_BUSINESS_ID || !license.isActive()) {
            auth().signOut()
            onResult(false, "আগে বৈধ License Activate করতে হবে।", null)
            return
        }

        val uid = firebaseUser.uid
        val normalizedEmail = email.trim()

        BusinessMembershipManager.currentMember(business.businessId) { member ->
            if (member != null) {
                if (member.blocked) {
                    auth().signOut()
                    onResult(false, "এই Partner account Block করা হয়েছে।", null)
                } else if (!member.approved) {
                    auth().signOut()
                    onResult(false, "আপনার Partner access এখনো Admin অনুমোদন করেননি।", null)
                } else if (adminLogin && member.role != "OWNER") {
                    auth().signOut()
                    onResult(false, "এই Gmail Business Owner নয়।", null)
                } else if (!adminLogin && member.role != "PARTNER") {
                    auth().signOut()
                    onResult(false, "এই Gmail Partner account হিসেবে অনুমোদিত নয়।", null)
                } else {
                    val role = if (member.role == "OWNER") SecurityStorage.ROLE_ADMIN else SecurityStorage.ROLE_PARTNER
                    val user = CloudAccessUser(uid, normalizedEmail, role, true, false)
                    if (role == SecurityStorage.ROLE_ADMIN) BusinessAccountStorage.setOwnerUid(context, uid)
                    saveLocalLogin(context, user)
                    onResult(true, "", user)
                }
            } else if (adminLogin) {
                BusinessMembershipManager.createOrUpdateOwner(business.businessId, normalizedEmail) { ok, message ->
                    if (!ok) {
                        auth().signOut()
                        onResult(false, message, null)
                    } else {
                        val user = CloudAccessUser(uid, normalizedEmail, SecurityStorage.ROLE_ADMIN, true, false)
                        BusinessAccountStorage.setOwnerUid(context, uid)
                        saveLocalLogin(context, user)
                        onResult(true, "", user)
                    }
                }
            } else {
                BusinessMembershipManager.requestPartner(business.businessId, normalizedEmail) { ok, message ->
                    auth().signOut()
                    onResult(false, message, if (ok) CloudAccessUser(uid, normalizedEmail, SecurityStorage.ROLE_PARTNER, false, false) else null)
                }
            }
        }
    }

    fun validateCurrentSession(context: Context, onResult: (Boolean) -> Unit) {
        val user = auth().currentUser ?: run { onResult(false); return }
        db().collection(USERS).document(user.uid).get().addOnSuccessListener { snapshot ->
            val role = snapshot.getString("role").orEmpty()
            val approved = snapshot.getBoolean("approved") == true
            val blocked = snapshot.getBoolean("blocked") == true
            val email = snapshot.getString("email").orEmpty().ifBlank { user.email.orEmpty() }
            if (blocked) {
                SecurityStorage.logout(context); auth().signOut(); onResult(false); return@addOnSuccessListener
            }
            if (approved && (role == SecurityStorage.ROLE_ADMIN || role == SecurityStorage.ROLE_PARTNER)) {
                val cached = SecurityStorage.upsertGoogleUser(context, email, role, true, user.uid)
                SecurityStorage.login(context, cached); onResult(true)
            } else {
                SecurityStorage.logout(context); auth().signOut(); onResult(false)
            }
        }.addOnFailureListener { onResult(false) }
    }

    fun listUsers(onResult: (List<CloudAccessUser>, String?) -> Unit) {
        val contextBusinessId = ""
        onResult(emptyList(), "Legacy user list is no longer used.")
    }

    fun approvePartner(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        val businessId = BusinessAccountStorage.get(context).businessId
        BusinessMembershipManager.setPartnerAccess(businessId, uid, true, false, onResult)
    }

    fun rejectPendingPartner(uid: String, onResult: (Boolean, String) -> Unit) {
        onResult(false, "Partner আবেদনটি এখন Business Membership থেকে পরিচালিত হয়।")
    }

    fun blockPartner(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        val businessId = BusinessAccountStorage.get(context).businessId
        BusinessMembershipManager.setPartnerAccess(businessId, uid, false, true, onResult)
    }

    fun unblockPartner(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        val businessId = BusinessAccountStorage.get(context).businessId
        BusinessMembershipManager.setPartnerAccess(businessId, uid, true, false, onResult)
    }

    fun removePartner(context: Context, uid: String, onResult: (Boolean, String) -> Unit) {
        val businessId = BusinessAccountStorage.get(context).businessId
        BusinessMembershipManager.removeMember(businessId, uid, onResult)
    }

    fun listBusinessMembers(context: Context, onResult: (List<CloudAccessUser>, String?) -> Unit) {
        val businessId = BusinessAccountStorage.get(context).businessId
        BusinessMembershipManager.listMembers(businessId) { members, error ->
            onResult(
                members.map {
                    CloudAccessUser(
                        uid = it.uid,
                        email = it.email,
                        role = if (it.role == "OWNER") SecurityStorage.ROLE_ADMIN else SecurityStorage.ROLE_PARTNER,
                        approved = it.approved,
                        blocked = it.blocked
                    )
                },
                error
            )
        }
    }

    private fun cacheAndLogin(context: Context, user: CloudAccessUser, onResult: (Boolean, String) -> Unit) {
        BusinessAccountStorage.ensureInitialized(context)
        if (user.role == SecurityStorage.ROLE_ADMIN) {
            BusinessAccountStorage.setOwnerUid(context, user.uid)
            val business = BusinessAccountStorage.get(context)
            BusinessMembershipManager.createOrUpdateOwner(business.businessId, user.email) { ok, message ->
                if (!ok) {
                    auth().signOut()
                    onResult(false, message)
                    return@createOrUpdateOwner
                }
                saveLocalLogin(context, user)
                onResult(true, "")
            }
            return
        }
        saveLocalLogin(context, user)
        onResult(true, "")
    }

    private fun saveLocalLogin(context: Context, user: CloudAccessUser) {
        SecurityStorage.upsertGoogleUser(context, user.email, user.role, user.approved, user.uid)
        val local = SecurityStorage.findByGoogleEmail(context, user.email)
        if (local != null) SecurityStorage.login(context, local)
    }
}
