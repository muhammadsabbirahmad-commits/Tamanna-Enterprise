package com.tamanna.enterprise.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Syncs the app's business/settings SharedPreferences to the signed-in
 * Firebase user. Security-sensitive local login data is deliberately excluded.
 *
 * Cloud path:
 * users/{FirebaseAuth.uid}/data/{namespace}
 */
object CloudSyncManager {
    private const val USERS = "users"
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
        "tamanna_inventory_meta"
    )

    private val started = AtomicBoolean(false)
    @Volatile private var pulling = false

    private val listeners = mutableMapOf<String, SharedPreferences.OnSharedPreferenceChangeListener>()

    private fun auth() = FirebaseAuth.getInstance()
    private fun db() = FirebaseFirestore.getInstance()

    private fun userDataCollection() =
        db().collection(USERS).document(auth().currentUser?.uid ?: "")
            .collection(DATA)

    fun start(context: Context) {
        if (auth().currentUser == null) return
        if (started.compareAndSet(false, true)) {
            val appContext = context.applicationContext
            namespaces.forEach { namespace ->
                val prefs = appContext.getSharedPreferences(namespace, Context.MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    if (!pulling && auth().currentUser != null) {
                        syncNamespace(appContext, namespace)
                    }
                }
                listeners[namespace] = listener
                prefs.registerOnSharedPreferenceChangeListener(listener)
            }
        }
        syncAll(context)
    }

    fun pullThenSync(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val uid = auth().currentUser?.uid
        if (uid.isNullOrBlank()) {
            onComplete(false)
            return
        }

        pulling = true
        val appContext = context.applicationContext
        val refs = namespaces.map { namespace ->
            userDataCollection().document(namespace).get()
        }

        Tasks.whenAllSuccess<com.google.firebase.firestore.DocumentSnapshot>(refs)
            .addOnSuccessListener { snapshots ->
                var foundRemoteData = false

                snapshots.forEachIndexed { index, snapshot ->
                    if (!snapshot.exists()) return@forEachIndexed
                    val values = snapshot.get("values") as? Map<*, *> ?: return@forEachIndexed
                    foundRemoteData = true

                    val namespace = namespaces[index]
                    val editor = appContext
                        .getSharedPreferences(namespace, Context.MODE_PRIVATE)
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

                pulling = false
                start(appContext)
                syncAll(appContext) {
                    onComplete(foundRemoteData)
                }
            }
            .addOnFailureListener {
                pulling = false
                start(appContext)
                onComplete(false)
            }
    }

    fun syncAll(context: Context, onComplete: () -> Unit = {}) {
        if (auth().currentUser == null) {
            onComplete()
            return
        }

        val batch = db().batch()
        val appContext = context.applicationContext

        namespaces.forEach { namespace ->
            val values = toFirestoreMap(
                appContext.getSharedPreferences(namespace, Context.MODE_PRIVATE).all
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

    private fun syncNamespace(context: Context, namespace: String) {
        val uid = auth().currentUser?.uid ?: return
        val values = toFirestoreMap(
            context.getSharedPreferences(namespace, Context.MODE_PRIVATE).all
        )
        db().collection(USERS)
            .document(uid)
            .collection(DATA)
            .document(namespace)
            .set(
                mapOf(
                    "values" to values,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
    }

    private fun toFirestoreMap(source: Map<String, *>): Map<String, Any?> =
        source.mapValues { (_, value) ->
            when (value) {
                is Set<*> -> value.filterIsInstance<String>()
                else -> value
            }
        }
}
