package com.tamanna.enterprise.sync

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.security.SecurityStorage

/**
 * Syncs the app's business/settings SharedPreferences to the signed-in
 * Firebase user. Security-sensitive local login data is deliberately excluded.
 *
 * Cloud path:
 * businesses/{businessId}/data/{namespace}
 */
object CloudSyncManager {
    private const val BUSINESSES = "businesses"
    private const val DATA = "data"

    private val namespaces = listOf(
        "tamanna_enterprise_products",
        "tamanna_enterprise_sales",
        "tamanna_enterprise_purchases",
        "tamanna_enterprise_finance",
        "tamanna_enterprise_partners",
        "tamanna_enterprise_settings",
        "tamanna_customer_due",
        "tamanna_supplier_due",
        "tamanna_inventory_meta",
        "tamanna_enterprise_sale_transactions",
        "tamanna_enterprise_sale_returns",
        "tamanna_enterprise_activity_log"
    )

    private fun auth() = FirebaseAuth.getInstance()
    private fun db() = FirebaseFirestore.getInstance()

    private fun businessDataCollection(context: Context) =
        db().collection(BUSINESSES)
            .document(BusinessAccountStorage.get(context).businessId)
            .collection(DATA)

    private fun validBusinessId(context: Context): Boolean {
        val businessId = BusinessAccountStorage.get(context).businessId.trim()
        return businessId.isNotBlank() && businessId != BusinessStorage.LEGACY_BUSINESS_ID
    }

    private fun approvedMember(context: Context, onResult: (Boolean, Boolean) -> Unit) {
        if (!validBusinessId(context)) {
            onResult(false, false)
            return
        }

        val uid = auth().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onResult(false, false)
            return
        }

        val businessId = BusinessAccountStorage.get(context).businessId
        db().collection(BUSINESSES).document(businessId)
            .collection("members").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val approved = snapshot.exists() &&
                    snapshot.getBoolean("approved") == true &&
                    snapshot.getBoolean("blocked") != true
                val owner = approved && snapshot.getString("role") == "OWNER"
                onResult(approved, owner)
            }
            .addOnFailureListener {
                onResult(false, false)
            }
    }

    fun pullThenSync(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val appContext = context.applicationContext
        approvedMember(appContext) { approved, _ ->
            if (!approved) {
                onComplete(false)
                return@approvedMember
            }

            val refs = namespaces.map { namespace ->
                businessDataCollection(appContext).document(namespace).get()
            }

            Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(refs)
                .addOnSuccessListener { snapshots ->
                    var foundRemoteData = false

                    snapshots.forEachIndexed { index, snapshot ->
                        if (!snapshot.exists()) return@forEachIndexed
                        val values = snapshot.get("values") as? Map<*, *> ?: return@forEachIndexed
                        foundRemoteData = true

                        val namespace = namespaces[index]
                        val editor = BusinessStorage
                            .prefs(appContext, namespace)
                            .edit()
                            .clear()

                        values.forEach { (key, value) ->
                            if (key !is String || value == null) return@forEach
                            when (value) {
                                is String -> editor.putString(key, value)
                                is Boolean -> editor.putBoolean(key, value)
                                is Long -> editor.putLong(key, value)
                                is Double -> editor.putString(key, value.toString())
                                is Number -> editor.putString(key, value.toString())
                                is List<*> -> editor.putStringSet(
                                    key,
                                    value.filterIsInstance<String>().toSet()
                                )
                            }
                        }
                        editor.apply()
                    }
                    onComplete(foundRemoteData)
                }
                .addOnFailureListener {
                    onComplete(false)
                }
        }
    }

    fun migrateLegacyCloudData(context: Context, onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val appContext = context.applicationContext
        approvedMember(appContext) { approved, owner ->
            if (!approved || !owner || !SecurityStorage.canWrite(appContext)) {
                onComplete(false, "শুধু অনুমোদিত Business Owner পুরোনো Cloud Data Migration করতে পারবেন।")
                return@approvedMember
            }

            val uid = auth().currentUser?.uid
            if (uid.isNullOrBlank()) {
                onComplete(false, "Google Account সংযুক্ত নেই।")
                return@approvedMember
            }

            val legacyCollection = db().collection("users").document(uid).collection(DATA)
            legacyCollection.get()
                .addOnSuccessListener { legacySnapshots ->
                    if (legacySnapshots.isEmpty) {
                        onComplete(false, "পুরোনো Cloud Data পাওয়া যায়নি।")
                        return@addOnSuccessListener
                    }

                    val targetCollection = businessDataCollection(appContext)
                    val batch = db().batch()
                    var migrated = 0

                    legacySnapshots.documents.forEach { legacy ->
                        if (!legacy.exists()) return@forEach
                        val namespace = legacy.id
                        if (!namespaces.contains(namespace)) return@forEach
                        val target = targetCollection.document(namespace)
                        val values = legacy.get("values")
                        if (values is Map<*, *>) {
                            val existing = legacy.get("updatedAt")
                            val payload = mutableMapOf<String, Any?>("values" to values)
                            if (existing != null) payload["updatedAt"] = existing
                            batch.set(target, payload, SetOptions.merge())
                            migrated++
                        }
                    }

                    if (migrated == 0) {
                        onComplete(false, "পুরোনো Cloud Data-তে মাইগ্রেট করার মতো Business Data পাওয়া যায়নি।")
                        return@addOnSuccessListener
                    }

                    batch.commit()
                        .addOnSuccessListener {
                            onComplete(true, "পুরোনো Cloud Data নতুন Business Data-তে মাইগ্রেট হয়েছে।")
                        }
                        .addOnFailureListener {
                            onComplete(false, it.localizedMessage ?: "Cloud Migration ব্যর্থ হয়েছে।")
                        }
                }
                .addOnFailureListener {
                    onComplete(false, it.localizedMessage ?: "পুরোনো Cloud Data পড়া যায়নি।")
                }
        }
    }

    fun syncAll(context: Context, onComplete: () -> Unit = {}) {
        val appContext = context.applicationContext
        approvedMember(appContext) { approved, owner ->
            if (!approved || !owner || !SecurityStorage.canWrite(appContext)) {
                onComplete()
                return@approvedMember
            }

            val batch = db().batch()

            namespaces.forEach { namespace ->
                val values = toFirestoreMap(
                    BusinessStorage.prefs(appContext, namespace).all
                )
                val ref = businessDataCollection(appContext).document(namespace)
                batch.set(
                    ref,
                    mapOf(
                        "values" to values,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }

            batch.commit()
                .addOnCompleteListener { onComplete() }
        }
    }

    private fun toFirestoreMap(source: Map<String, *>): Map<String, Any?> =
        source.mapValues { (_, value) ->
            when (value) {
                is Set<*> -> value.filterIsInstance<String>()
                else -> value
            }
        }
}
