package com.tamanna.enterprise.business

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/**
 * Read-only access list for a specific business.
 *
 * One document is stored per authorized Gmail:
 * businesses/{businessId}/viewAccess/{normalizedEmail}
 *
 * This step only defines the data layer. UI, login routing and security rules
 * are handled in the following steps.
 */
data class ViewAccessEntry(
    val email: String,
    val businessId: String,
    val ownerUid: String,
    val active: Boolean
)

object ViewAccessManager {
    private const val BUSINESSES = "businesses"
    private const val VIEW_ACCESS = "viewAccess"

    private fun db() = FirebaseFirestore.getInstance()
    private fun auth() = FirebaseAuth.getInstance()

    private fun normalizeEmail(email: String): String =
        email.trim().lowercase()

    fun addOrUpdate(
        businessId: String,
        email: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val uid = auth().currentUser?.uid
        val cleanEmail = normalizeEmail(email)

        if (uid.isNullOrBlank()) {
            onResult(false, "Google authentication পাওয়া যায়নি।")
            return
        }
        if (businessId.isBlank()) {
            onResult(false, "Business ID পাওয়া যায়নি।")
            return
        }
        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            onResult(false, "সঠিক Gmail ঠিকানা দিন।")
            return
        }

        val businessRef = db().collection(BUSINESSES).document(businessId)
        businessRef.get()
            .addOnSuccessListener { business ->
                if (!business.exists()) {
                    onResult(false, "Business পাওয়া যায়নি।")
                    return@addOnSuccessListener
                }

                if (business.getString("ownerUid") != uid) {
                    onResult(false, "শুধু Business Owner View Access দিতে পারবেন।")
                    return@addOnSuccessListener
                }

                businessRef.collection(VIEW_ACCESS)
                    .document(cleanEmail)
                    .set(
                        mapOf(
                            "email" to cleanEmail,
                            "businessId" to businessId,
                            "ownerUid" to uid,
                            "active" to true,
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        onResult(true, "View Access Gmail সংরক্ষণ হয়েছে।")
                    }
                    .addOnFailureListener {
                        onResult(false, it.localizedMessage ?: "View Access সংরক্ষণ করা যায়নি।")
                    }
            }
            .addOnFailureListener {
                onResult(false, it.localizedMessage ?: "Business যাচাই করা যায়নি।")
            }
    }

    fun remove(
        businessId: String,
        email: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val uid = auth().currentUser?.uid
        val cleanEmail = normalizeEmail(email)

        if (uid.isNullOrBlank()) {
            onResult(false, "Google authentication পাওয়া যায়নি।")
            return
        }

        val businessRef = db().collection(BUSINESSES).document(businessId)
        businessRef.get()
            .addOnSuccessListener { business ->
                if (business.getString("ownerUid") != uid) {
                    onResult(false, "শুধু Business Owner View Access পরিবর্তন করতে পারবেন।")
                    return@addOnSuccessListener
                }

                businessRef.collection(VIEW_ACCESS).document(cleanEmail).delete()
                    .addOnSuccessListener { onResult(true, "View Access সরানো হয়েছে।") }
                    .addOnFailureListener {
                        onResult(false, it.localizedMessage ?: "View Access সরানো যায়নি।")
                    }
            }
            .addOnFailureListener {
                onResult(false, it.localizedMessage ?: "Business যাচাই করা যায়নি।")
            }
    }

    fun findForEmailAcrossBusinesses(
        email: String,
        onResult: (ViewAccessEntry?) -> Unit
    ) {
        val cleanEmail = normalizeEmail(email)
        if (cleanEmail.isBlank()) {
            onResult(null)
            return
        }

        db().collectionGroup(VIEW_ACCESS)
            .whereEqualTo("email", cleanEmail)
            .whereEqualTo("active", true)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc == null) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                onResult(
                    ViewAccessEntry(
                        email = doc.getString("email").orEmpty(),
                        businessId = doc.getString("businessId").orEmpty(),
                        ownerUid = doc.getString("ownerUid").orEmpty(),
                        active = doc.getBoolean("active") == true
                    )
                )
            }
            .addOnFailureListener { onResult(null) }
    }

    fun findForEmail(
        businessId: String,
        email: String,
        onResult: (ViewAccessEntry?) -> Unit
    ) {
        val cleanEmail = normalizeEmail(email)
        if (businessId.isBlank() || cleanEmail.isBlank()) {
            onResult(null)
            return
        }

        db().collection(BUSINESSES).document(businessId)
            .collection(VIEW_ACCESS).document(cleanEmail).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists() || doc.getBoolean("active") != true) {
                    onResult(null)
                    return@addOnSuccessListener
                }

                onResult(
                    ViewAccessEntry(
                        email = doc.getString("email").orEmpty(),
                        businessId = doc.getString("businessId").orEmpty(),
                        ownerUid = doc.getString("ownerUid").orEmpty(),
                        active = doc.getBoolean("active") == true
                    )
                )
            }
            .addOnFailureListener { onResult(null) }
    }

    fun listForBusiness(
        businessId: String,
        onResult: (List<ViewAccessEntry>) -> Unit
    ) {
        if (businessId.isBlank()) {
            onResult(emptyList())
            return
        }

        db().collection(BUSINESSES).document(businessId)
            .collection(VIEW_ACCESS)
            .get()
            .addOnSuccessListener { snapshot ->
                onResult(
                    snapshot.documents.mapNotNull { doc ->
                        if (doc.getBoolean("active") != true) return@mapNotNull null
                        ViewAccessEntry(
                            email = doc.getString("email").orEmpty(),
                            businessId = doc.getString("businessId").orEmpty(),
                            ownerUid = doc.getString("ownerUid").orEmpty(),
                            active = true
                        )
                    }.sortedBy { it.email }
                )
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

}
