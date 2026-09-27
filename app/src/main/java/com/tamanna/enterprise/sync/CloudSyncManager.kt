package com.tamanna.enterprise.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.tamanna.enterprise.business.BusinessStorage

object CloudSyncManager {
    private const val PREF_SYNC = "tamanna_sync_prefs"
    private const val KEY_LAST_LOCAL_HASH = "last_local_hash"

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

    // সরাসরি ইউজারের UID দিয়ে ক্লাউড ফোল্ডার তৈরি হবে (কোনো ঝামেলা ছাড়াই)
    private fun userSyncDoc(context: Context): DocumentReference {
        val uid = auth().currentUser?.uid ?: "default_admin"
        return db().collection("users").document(uid).collection("data").document("cloud_backup_sync")
    }

    private fun calculateLocalHash(context: Context): String {
        val sb = java.lang.StringBuilder()
        namespaces.forEach { ns ->
            val prefs = BusinessStorage.prefs(context, ns).all
            prefs.keys.sorted().forEach { key -> sb.append(key).append("=").append(prefs[key].toString()).append(";") }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(sb.toString().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun toFirestoreMap(source: Map<String, *>): Map<String, Any?> =
        source.mapValues { (_, value) -> if (value is Set<*>) value.filterIsInstance<String>() else value }

    // --- Smart Sync (Hash-based) ---
    fun smartSync(context: Context, isAuto: Boolean = false, onComplete: (String) -> Unit) {
        val appContext = context.applicationContext
        val currentUser = auth().currentUser
        
        if (currentUser == null) {
            if (!isAuto) onComplete("কোনো অ্যাকাউন্ট লগইন করা নেই।")
            return
        }

        val currentLocalHash = calculateLocalHash(appContext)
        val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
        val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""

        val metaRef = userSyncDoc(appContext)

        metaRef.get().addOnSuccessListener { doc ->
            val cloudHash = doc.getString("dataHash") ?: ""

            when {
                cloudHash.isNotBlank() && cloudHash != lastSyncedHash && cloudHash != currentLocalHash -> {
                    pullAllFromCloud(appContext, cloudHash) { success ->
                        if (!isAuto) onComplete(if (success) "ক্লাউড থেকে নতুন ডেটা রিস্টোর হয়েছে। ☁️⬇️" else "রিস্টোর ব্যর্থ হয়েছে।")
                    }
                }
                currentLocalHash != lastSyncedHash -> {
                    pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                        if (!isAuto) onComplete(if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "ক্লাউডে সেভ ব্যর্থ হয়েছে।")
                    }
                }
                else -> {
                    if (!isAuto) onComplete("সব ডেটা আপ-টু-데이트 আছে। ✅")
                }
            }
        }.addOnFailureListener {
            pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                if (!isAuto) onComplete(if (success) "প্রাথমিক ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "সিঙ্ক ব্যর্থ হয়েছে।")
            }
        }
    }

    private fun pushAllToCloud(context: Context, currentHash: String, metaRef: DocumentReference, onComplete: (Boolean) -> Unit) {
        val batch = db().batch()
        namespaces.forEach { namespace ->
            val values = toFirestoreMap(BusinessStorage.prefs(context, namespace).all)
            batch.set(userSyncDoc(context).collection("namespaces").document(namespace), mapOf("values" to values), SetOptions.merge())
        }
        batch.set(metaRef, mapOf("dataHash" to currentHash, "updatedBy" to auth().currentUser?.uid), SetOptions.merge())

        batch.commit().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).edit().putString(KEY_LAST_LOCAL_HASH, currentHash).apply()
                onComplete(true)
            } else onComplete(false)
        }
    }

    private fun pullAllFromCloud(context: Context, newCloudHash: String, onComplete: (Boolean) -> Unit) {
        val refs = namespaces.map { userSyncDoc(context).collection("namespaces").document(it).get() }
        Tasks.whenAllSuccess<DocumentSnapshot>(refs).addOnSuccessListener { snapshots ->
            snapshots.forEachIndexed { index, snapshot ->
                val editor = BusinessStorage.prefs(context, namespaces[index]).edit().clear()
                if (snapshot.exists()) {
                    val values = snapshot.get("values") as? Map<*, *>
                    values?.forEach { (key, value) ->
                        if (key !is String || value == null) return@forEach
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Long -> editor.putLong(key, value)
                            is Double -> editor.putString(key, value.toString())
                            is Number -> editor.putString(key, value.toString())
                            is List<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }
                editor.apply()
            }
            context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).edit().putString(KEY_LAST_LOCAL_HASH, newCloudHash).apply()
            onComplete(true)
        }.addOnFailureListener { onComplete(false) }
    }

    // --- পার্মানেন্ট অটো-সিঙ্ক লজিক ---
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var syncContextGlobal: Context? = null
    private var cloudListener: ListenerRegistration? = null

    private val localCheckRunnable = object : Runnable {
        override fun run() {
            if (isAutoSyncEnabled()) {
                val appContext = syncContextGlobal?.applicationContext ?: return
                val currentHash = calculateLocalHash(appContext)
                val lastHash = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).getString(KEY_LAST_LOCAL_HASH, "")
                
                if (currentHash != lastHash) {
                    smartSync(appContext, isAuto = true) {}
                }
                handler.postDelayed(this, 15000)
            }
        }
    }

    fun startAutoSync(context: Context) {
        val appContext = context.applicationContext
        syncContextGlobal = appContext
        
        appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            .edit().putBoolean("auto_sync_active", true).apply()

        if (isRunning || auth().currentUser == null) return
        isRunning = true

        val metaRef = userSyncDoc(appContext)
        cloudListener = metaRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            
            val cloudHash = snapshot.getString("dataHash") ?: ""
            val lastHash = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).getString(KEY_LAST_LOCAL_HASH, "")
            
            if (cloudHash.isNotBlank() && cloudHash != lastHash) {
                smartSync(appContext, isAuto = true) {}
            }
        }

        handler.post(localCheckRunnable)
    }

    fun stopAutoSync() {
        isRunning = false
        syncContextGlobal?.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("auto_sync_active", false)?.apply()

        handler.removeCallbacks(localCheckRunnable)
        cloudListener?.remove()
        cloudListener = null
    }

    fun isAutoSyncEnabled(): Boolean {
        val ctx = syncContextGlobal ?: return false
        return ctx.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).getBoolean("auto_sync_active", false)
    }
}
