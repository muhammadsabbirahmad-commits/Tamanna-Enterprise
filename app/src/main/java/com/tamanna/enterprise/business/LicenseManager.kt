package com.tamanna.enterprise.business

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

object LicenseManager {
    private const val LICENSES = "licenses"

    private fun db() = FirebaseFirestore.getInstance()

    fun verify(
        code: String,
        onResult: (Boolean, LicenseInfo?, String) -> Unit
    ) {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) {
            onResult(false, null, "License Code দিন।")
            return
        }

        db().collection(LICENSES).document(clean).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    onResult(false, null, "এই License Code পাওয়া যায়নি।")
                    return@addOnSuccessListener
                }

                val info = LicenseInfo(
                    code = clean,
                    businessId = snapshot.getString("businessId").orEmpty(),
                    businessName = snapshot.getString("businessName").orEmpty(),
                    type = snapshot.getString("type").orEmpty().ifBlank { "CUSTOM" },
                    status = snapshot.getString("status").orEmpty().ifBlank { LicenseStatus.PENDING.name },
                    expiresAt = snapshot.getLong("expiresAt") ?: 0L,
                    deviceLimit = (snapshot.getLong("deviceLimit") ?: 1L).toInt().coerceAtLeast(1)
                )

                when {
                    info.businessId.isBlank() ->
                        onResult(false, info, "License-এর Business ID সেট করা নেই।")
                    info.status != LicenseStatus.ACTIVE.name ->
                        onResult(false, info, "এই License এখন ${info.status} অবস্থায় আছে।")
                    info.expiresAt > 0L && info.expiresAt <= System.currentTimeMillis() ->
                        onResult(false, info.copy(status = LicenseStatus.EXPIRED.name), "এই License-এর মেয়াদ শেষ হয়েছে।")
                    else ->
                        onResult(true, info, "License সক্রিয় আছে।")
                }
            }
            .addOnFailureListener {
                onResult(false, null, it.localizedMessage ?: "License যাচাই করা যায়নি।")
            }
    }

    fun saveVerifiedLicense(context: Context, info: LicenseInfo) {
        LicenseStorage.save(context, info)
        BusinessAccountStorage.ensureInitialized(context)
        BusinessAccountStorage.setActiveBusinessId(context, info.businessId)
        BusinessAccountStorage.updateName(
            context,
            info.businessName.ifBlank { BusinessAccountStorage.get(context).businessName }
        )
        BusinessAccountStorage.setLicenseCode(context, info.code)
    }
}
