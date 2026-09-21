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
        if (businessId.isBlank() || uid.isBlank()) {
            onResult(false, "Business বা Partner ID পাওয়া যায়নি।")
            return
        }

        db().collection(BUSINESSES).document(businessId)
            .set(
                mapOf(
                    "businessId" to businessId,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                db().collection(BUSINESSES).document(businessId)
                    .collection(MEMBERS).document(uid)
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
            }
            .addOnFailureListener {
                onResult(false, it.localizedMessage ?: "Business membership সংরক্ষণ করা যায়নি।")
            }
    }
}
