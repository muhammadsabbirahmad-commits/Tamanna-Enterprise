package com.tamanna.enterprise.business

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

data class BusinessAccount(
    val businessId: String,
    val businessName: String,
    val ownerUid: String,
    val licenseCode: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object BusinessAccountStorage {
    private const val PREFS = "tamanna_business_account"
    private const val KEY_ID = "business_id"
    private const val KEY_NAME = "business_name"
    private const val KEY_OWNER_UID = "owner_uid"
    private const val KEY_LICENSE = "license_code"
    private const val KEY_CREATED_AT = "created_at"

    const val LEGACY_BUSINESS_ID = "legacy-business"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context): BusinessAccount {
        // ডেটা না থাকলে যেন ক্র্যাশ না করে তাই আগে থেকেই নিশ্চিত করা হচ্ছে
        ensureInitialized(context)
        
        val p = prefs(context)
        return BusinessAccount(
            businessId = p.getString(KEY_ID, LEGACY_BUSINESS_ID)
                ?.trim().takeUnless { it.isNullOrBlank() } ?: LEGACY_BUSINESS_ID,
            businessName = p.getString(KEY_NAME, "Tamanna Enterprise")
                ?.trim().takeUnless { it.isNullOrBlank() } ?: "Tamanna Enterprise",
            ownerUid = p.getString(KEY_OWNER_UID, "").orEmpty(),
            licenseCode = p.getString(KEY_LICENSE, "").orEmpty(),
            createdAt = p.getLong(KEY_CREATED_AT, 0L)
        )
    }

    fun ensureInitialized(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_ID)) {
            p.edit()
                .putString(KEY_ID, LEGACY_BUSINESS_ID)
                .putString(KEY_NAME, "Tamanna Enterprise")
                .putLong(KEY_CREATED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    fun create(
        context: Context,
        businessName: String,
        ownerUid: String,
        businessId: String = generateBusinessId()
    ): BusinessAccount {
        val cleanName = businessName.trim().ifBlank { "Tamanna Enterprise" }
        val cleanId = businessId.trim().ifBlank { generateBusinessId() }
        prefs(context).edit()
            .putString(KEY_ID, cleanId)
            .putString(KEY_NAME, cleanName)
            .putString(KEY_OWNER_UID, ownerUid.trim())
            .putLong(KEY_CREATED_AT, System.currentTimeMillis())
            .apply()
        return get(context)
    }

    fun updateName(context: Context, businessName: String) {
        prefs(context).edit()
            .putString(KEY_NAME, businessName.trim().ifBlank { "Tamanna Enterprise" })
            .apply()
    }

    fun setOwnerUid(context: Context, ownerUid: String) {
        prefs(context).edit().putString(KEY_OWNER_UID, ownerUid.trim()).apply()
    }

    fun setLicenseCode(context: Context, licenseCode: String) {
        prefs(context).edit().putString(KEY_LICENSE, licenseCode.trim()).apply()
    }

    fun setActiveBusinessId(context: Context, businessId: String) {
        val clean = businessId.trim()
        require(clean.isNotBlank()) { "businessId cannot be blank" }
        prefs(context).edit().putString(KEY_ID, clean).apply()
    }


    /**
     * Assign each approved Firebase user a private business namespace.
     * The original legacy namespace is retained only for the UID that owns
     * the existing legacy-business Firestore document.
     */
    fun activateForUser(
        context: Context,
        ownerUid: String,
        email: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val uid = ownerUid.trim()
        if (uid.isBlank()) {
            onResult(false, "Gmail account identity পাওয়া যায়নি।")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val legacyRef = db.collection("businesses").document(LEGACY_BUSINESS_ID)
        legacyRef.get()
            .addOnSuccessListener { legacyDoc ->
                val legacyOwner = legacyDoc.getString("ownerUid").orEmpty()
                val targetId = if (legacyDoc.exists() && legacyOwner == uid) {
                    LEGACY_BUSINESS_ID
                } else {
                    "user-" + uid.replace(Regex("[^A-Za-z0-9_-]"), "_")
                }
                val targetRef = db.collection("businesses").document(targetId)

                fun activate() {
                    prefs(context).edit()
                        .putString(KEY_ID, targetId)
                        .putString(KEY_NAME, "Tamanna Enterprise")
                        .putString(KEY_OWNER_UID, uid)
                        .putLong(KEY_CREATED_AT, System.currentTimeMillis())
                        .apply()
                    onResult(true, "")
                }

                if (targetId == LEGACY_BUSINESS_ID) {
                    activate()
                    return@addOnSuccessListener
                }

                targetRef.get()
                    .addOnSuccessListener { existing ->
                        if (existing.exists()) {
                            if (existing.getString("ownerUid") != uid) {
                                onResult(false, "এই Gmail-এর ব্যবসার পরিচয় যাচাই করা যায়নি।")
                            } else {
                                activate()
                            }
                        } else {
                            val data = hashMapOf(
                                "businessId" to targetId,
                                "businessName" to "Tamanna Enterprise",
                                "ownerUid" to uid,
                                "ownerEmail" to email.trim().lowercase(),
                                "createdAt" to System.currentTimeMillis(),
                                "schemaVersion" to 1
                            )
                            targetRef.set(data)
                                .addOnSuccessListener { activate() }
                                .addOnFailureListener {
                                    onResult(false, "আপনার আলাদা হিসাবের জায়গা তৈরি করা যায়নি: ${it.localizedMessage ?: "আবার চেষ্টা করুন।"}")
                                }
                        }
                    }
                    .addOnFailureListener {
                        onResult(false, "আপনার ব্যবসার পরিচয় যাচাই করা যায়নি: ${it.localizedMessage ?: "আবার চেষ্টা করুন।"}")
                    }
            }
            .addOnFailureListener {
                onResult(false, "পুরোনো হিসাবের মালিকানা যাচাই করা যায়নি: ${it.localizedMessage ?: "ইন্টারনেট পরীক্ষা করুন।"}")
            }
    }

    private fun generateBusinessId(): String =
        "biz-" + UUID.randomUUID().toString().replace("-", "").take(16)
}
