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
 * users/{FirebaseAuth.uid}/data/{namespace}
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

    private fun businessDataCollection() =
        db().collection(BUSINESSES)
            .document(BusinessAccountStorage.get().businessId)
            .collection(DATA)

    fun pullThenSync(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onComplete(false)
            return
        }
        val appContext = context.applicationContext
        val refs = namespaces.map { namespace ->
            businessDataCollection().document(namespace).get()
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

    fun syncAll(context: Context, onComplete: () -> Unit = {}) {
        if (auth().currentUser == null || !SecurityStorage.canWrite(context)) {
            onComplete()
            return
        }

        val batch = db().batch()
        val appContext = context.applicationContext

        namespaces.forEach { namespace ->
            val values = toFirestoreMap(
                BusinessStorage.prefs(appContext, namespace).all
            )
            val ref = userDataCollection().document(namespace)
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

    private fun toFirestoreMap(source: Map<String, *>): Map<String, Any?> =
        source.mapValues { (_, value) ->
            when (value) {
                is Set<*> -> value.filterIsInstance<String>()
                else -> value
            }
        }
}
