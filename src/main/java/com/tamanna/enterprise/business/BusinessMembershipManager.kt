package com.tamanna.enterprise.business

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

data class BusinessMember(
    val uid: String,
    val email: String,
    val role: String,
    val approved: Boolean,
    val blocked: Boolean
)

object BusinessMembershipManager {
    private const val BUSINESSES = "businesses"
    private const val MEMBERS = "members"

    private fun db() = FirebaseFirestore.getInstance()
    private fun auth() = FirebaseAuth.getInstance()

    fun currentMember(businessId: String, onResult: (BusinessMember?) -> Unit) {
        val uid = auth().currentUser?.uid
        if (uid.isNullOrBlank() || businessId.isBlank()) {
            onResult(null)
            return
        }

        db().collection(BUSINESSES).document(businessId)
            .collection(MEMBERS).document(uid).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                onResult(
                    BusinessMember(
                        uid = uid,
                        email = d.getString("email").orEmpty(),
                        role = d.getString("role").orEmpty(),
                        approved = d.getBoolean("approved") == true,
                        blocked = d.getBoolean("blocked") == true
                    )
                )
            }
            .addOnFailureListener { onResult(null) }
    }

    fun createOrUpdateOwner(businessId: String, email: String, onResult: (Boolean, String) -> Unit) {
        val uid = auth().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onResult(false, "Google authentication পাওয়া যায়নি।")
            return
        }
        if (businessId.isBlank()) {
            onResult(false, "Business ID পাওয়া যায়নি।")
            return
        }

        val businessRef = db().collection(BUSINESSES).document(businessId)
        businessRef.get()
            .addOnSuccessListener { existing ->
                val existingOwnerUid = existing.getString("ownerUid").orEmpty()
                if (existingOwnerUid.isNotBlank() && existingOwnerUid != uid) {
                    onResult(false, "এই Business-এর Owner ইতিমধ্যে অন্য Account হিসেবে নির্ধারিত আছে।")
                    return@addOnSuccessListener
                }

                businessRef.set(
                    mapOf(
                        "businessId" to businessId,
                        "ownerUid" to uid,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).addOnSuccessListener {
                    businessRef.collection(MEMBERS).document(uid)
                        .set(
                            mapOf(
                                "uid" to uid,
                                "email" to email,
                                "role" to "OWNER",
                                "approved" to true,
                                "blocked" to false,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        )
                        .addOnSuccessListener { onResult(true, "Business Owner membership তৈরি হয়েছে।") }
                        .addOnFailureListener {
                            onResult(false, it.localizedMessage ?: "Business membership তৈরি করা যায়নি.")
                        }
                }.addOnFailureListener {
                    onResult(false, it.localizedMessage ?: "Business Owner সংরক্ষণ করা যায়নি.")
                }
            }
            .addOnFailureListener {
                onResult(false, it.localizedMessage ?: "Business Owner যাচাই করা যায়নি.")
            }
    }

    fun createOrUpdatePartner(
        businessId: String,
        uid: String,
        email: String,
        blocked: Boolean = false,
        onResult: (Boolean, String) -> Unit
    ) {
        val currentUid = auth().currentUser?.uid
        if (currentUid.isNullOrBlank() || businessId.isBlank() || uid.isBlank()) {
            onResult(false, "Business বা Partner ID পাওয়া যায়নি।")
            return
        }

        val businessRef = db().collection(BUSINESSES).document(businessId)
        businessRef.get().addOnSuccessListener { business ->
            if (!business.exists() || business.getString("ownerUid") != currentUid) {
                onResult(false, "শুধু Business Owner Partner access পরিচালনা করতে পারবেন।")
                return@addOnSuccessListener
            }
            if (uid == currentUid) {
                onResult(false, "Owner account-কে Partner হিসেবে পরিবর্তন করা যাবে না।")
                return@addOnSuccessListener
            }

            businessRef.collection(MEMBERS).document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "email" to email,
                        "role" to "PARTNER",
                        "approved" to !blocked,
                        "blocked" to blocked,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .addOnSuccessListener { onResult(true, "Partner Business membership সংরক্ষণ হয়েছে।") }
                .addOnFailureListener {
                    onResult(false, it.localizedMessage ?: "Partner membership সংরক্ষণ করা যায়নি।")
                }
        }.addOnFailureListener {
            onResult(false, it.localizedMessage ?: "Business Owner যাচাই করা যায়নি।")
        }
    }

    fun requestPartner(businessId: String, email: String, onResult: (Boolean, String) -> Unit) {
        val user = auth().currentUser
        val uid = user?.uid
        val verifiedEmail = user?.email?.trim().orEmpty()
        if (uid.isNullOrBlank() || businessId.isBlank() || verifiedEmail.isBlank()) {
            onResult(false, "Partner account তথ্য পাওয়া যায়নি।")
            return
        }
        val businessRef = db().collection(BUSINESSES).document(businessId)
        businessRef.get().addOnSuccessListener { business ->
            if (!business.exists() || business.getString("ownerUid").isNullOrBlank()) {
                onResult(false, "Business Owner এখনো সেটআপ হয়নি।")
                return@addOnSuccessListener
            }
            businessRef.collection(MEMBERS).document(uid).set(
                mapOf(
                    "uid" to uid,
                    "email" to verifiedEmail,
                    "role" to "PARTNER",
                    "approved" to false,
                    "blocked" to false,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).addOnSuccessListener {
                onResult(true, "Partner আবেদন Admin অনুমোদনের অপেক্ষায় আছে।")
            }.addOnFailureListener {
                onResult(false, it.localizedMessage ?: "Partner আবেদন সংরক্ষণ করা যায়নি।")
            }
        }.addOnFailureListener {
            onResult(false, it.localizedMessage ?: "Business যাচাই করা যায়নি।")
        }
    }

    fun listMembers(businessId: String, onResult: (List<BusinessMember>, String?) -> Unit) {
        if (businessId.isBlank()) {
            onResult(emptyList(), "Business ID পাওয়া যায়নি।")
            return
        }
        db().collection(BUSINESSES).document(businessId).collection(MEMBERS).get()
            .addOnSuccessListener { snapshot ->
                val members = snapshot.documents.mapNotNull { d ->
                    val uid = d.id
                    val email = d.getString("email").orEmpty()
                    val role = d.getString("role").orEmpty()
                    if (uid.isBlank() || email.isBlank() || role.isBlank()) null
                    else BusinessMember(
                        uid = uid,
                        email = email,
                        role = role,
                        approved = d.getBoolean("approved") == true,
                        blocked = d.getBoolean("blocked") == true
                    )
                }
                onResult(members.sortedWith(compareBy<BusinessMember> { it.approved }.thenBy { it.email.lowercase() }), null)
            }
            .addOnFailureListener { onResult(emptyList(), it.localizedMessage ?: "Business member তালিকা আনা যায়নি।") }
    }

    fun setPartnerAccess(businessId: String, uid: String, approved: Boolean, blocked: Boolean, onResult: (Boolean, String) -> Unit) {
        if (businessId.isBlank() || uid.isBlank()) {
            onResult(false, "Business বা Partner ID পাওয়া যায়নি।")
            return
        }
        db().collection(BUSINESSES).document(businessId).collection(MEMBERS).document(uid)
            .update(
                mapOf(
                    "approved" to approved,
                    "blocked" to blocked,
                    "role" to "PARTNER",
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener { onResult(true, "Partner access update হয়েছে।") }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "Partner access update করা যায়নি।") }
    }

    fun removeMember(businessId: String, uid: String, onResult: (Boolean, String) -> Unit) {
        if (businessId.isBlank() || uid.isBlank()) {
            onResult(false, "Business বা Partner ID পাওয়া যায়নি।")
            return
        }
        db().collection(BUSINESSES).document(businessId).collection(MEMBERS).document(uid)
            .delete()
            .addOnSuccessListener { onResult(true, "Partner Remove করা হয়েছে।") }
            .addOnFailureListener { onResult(false, it.localizedMessage ?: "Partner Remove করা যায়নি।") }
    }
}