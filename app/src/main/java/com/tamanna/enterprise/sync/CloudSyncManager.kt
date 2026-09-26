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
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.security.SecurityStorage

object CloudSyncManager {
    private const val BUSINESSES = "businesses"
    private const val DATA = "data"
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

    private fun businessDataCollection(context: Context) =
        db().collection(BUSINESSES)
            .document(BusinessAccountStorage.get(context).businessId)
            .collection(DATA)

    private fun validBusinessId(context: Context): Boolean {
        val businessId = BusinessAccountStorage.get(context).businessId.trim()
        return businessId.isNotBlank() && businessId != BusinessStorage.LEGACY_BUSINESS_ID
    }

    private fun approvedMember(context: Context, onResult: (Boolean, Boolean) -> Unit) {
        if (!validBusinessId(context)) { onResult(false, false); return }
        val uid = auth().currentUser?.uid
        if (uid.isNullOrBlank()) { onResult(false, false); return }

        val businessId = BusinessAccountStorage.get(context).businessId
        db().collection(BUSINESSES).document(businessId)
            .collection("members").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val approved = snapshot.exists() && snapshot.getBoolean("approved") == true && snapshot.getBoolean("blocked") != true
                val owner = approved && snapshot.getString("role") == "OWNER"
                onResult(approved, owner)
            }.addOnFailureListener { onResult(false, false) }
    }

    // ডেটার ইউনিক ফিঙ্গারপ্রিন্ট (Hash)
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
        approvedMember(appContext) { approved, _ ->
            if (!approved || !SecurityStorage.canWrite(appContext)) {
                if (!isAuto) onComplete("সিঙ্ক করার অনুমতি নেই।")
                return@approvedMember
            }

            val currentLocalHash = calculateLocalHash(appContext)
            val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""

            val metaRef = businessDataCollection(appContext).document("_sync_meta")

            metaRef.get().addOnSuccessListener { doc ->
                val cloudHash = doc.getString("dataHash") ?: ""

                when {
                    // ১. ক্লাউডে নতুন ডেটা আছে, যা আমাদের মোবাইলের শেষ সিঙ্কের সাথে মিলছে না (IN/Download)
                    cloudHash.isNotBlank() && cloudHash != lastSyncedHash && cloudHash != currentLocalHash -> {
                        pullAllFromCloud(appContext, cloudHash) { success ->
                            if (!isAuto) onComplete(if (success) "ক্লাউড থেকে নতুন ডেটা রিস্টোর হয়েছে। ☁️⬇️" else "রিস্টোর ব্যর্থ হয়েছে।")
                        }
                    }
                    // ২. আমাদের মোবাইলে নতুন কাজ হয়েছে, যা ক্লাউডে নেই (OUT/Upload)
                    currentLocalHash != lastSyncedHash -> {
                        pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                            if (!isAuto) onComplete(if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "ক্লাউডে সেভ ব্যর্থ হয়েছে।")
                        }
                    }
                    // ৩. সবকিছু একদম ঠিক আছে
                    else -> {
                        if (!isAuto) onComplete("সব ডেটা আপ-টু-ডেট আছে। ✅")
                    }
                }
            }.addOnFailureListener {
                // মেটা ফাইল না থাকলে (প্রথমবারের সিঙ্ক)
                pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                    if (!isAuto) onComplete(if (success) "প্রাথমিক ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "সিঙ্ক ব্যর্থ হয়েছে।")
                }
            }
        }
    }

    private fun pushAllToCloud(context: Context, currentHash: String, metaRef: DocumentReference, onComplete: (Boolean) -> Unit) {
        val batch = db().batch()
        namespaces.forEach { namespace ->
            val values = toFirestoreMap(BusinessStorage.prefs(context, namespace).all)
            batch.set(businessDataCollection(context).document(namespace), mapOf("values" to values), SetOptions.merge())
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
        val refs = namespaces.map { businessDataCollection(context).document(it).get() }
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

    // --- সম্পূর্ণ নতুন ও রিয়েল-টাইম অটো-সিঙ্ক লজিক ---
    private val handler = Handler(Looper.getMainLooper())
    private var isAutoSyncing = false
    private lateinit var syncContext: Context
    private var cloudListener: ListenerRegistration? = null

    private val localCheckRunnable = object : Runnable {
        override fun run() {
            if (isAutoSyncing) {
                val appContext = syncContext.applicationContext
                val currentHash = calculateLocalHash(appContext)
                val lastHash = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).getString(KEY_LAST_LOCAL_HASH, "")
                
                // ১৫ সেকেন্ড পর পর শুধু চেক করবে লোকাল মোবাইলে নতুন কাজ হয়েছে কি না (OUT)
                if (currentHash != lastHash) {
                    smartSync(appContext, isAuto = true) {}
                }
                handler.postDelayed(this, 15000)
            }
        }
    }

    fun startAutoSync(context: Context) {
        if (isAutoSyncing || !validBusinessId(context)) return
        syncContext = context.applicationContext
        isAutoSyncing = true

        // ১. রিয়েল-টাইম ক্লাউড চেকার (IN) - কেউ ক্লাউডে সেভ করা মাত্রই ইনস্ট্যান্ট ডাউনলোড হবে
        val metaRef = businessDataCollection(syncContext).document("_sync_meta")
        cloudListener = metaRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            
            val cloudHash = snapshot.getString("dataHash") ?: ""
            val lastHash = syncContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).getString(KEY_LAST_LOCAL_HASH, "")
            
            if (cloudHash.isNotBlank() && cloudHash != lastHash) {
                smartSync(syncContext, isAuto = true) {}
            }
        }

        // ২. ১৫ সেকেন্ড টাইমার (OUT) - লোকাল কাজগুলো আপলোড করার জন্য
        handler.post(localCheckRunnable)
    }

    fun stopAutoSync() {
        isAutoSyncing = false
        handler.removeCallbacks(localCheckRunnable)
        cloudListener?.remove()
        cloudListener = null
    }

    fun isAutoSyncEnabled(): Boolean = isAutoSyncing
}
