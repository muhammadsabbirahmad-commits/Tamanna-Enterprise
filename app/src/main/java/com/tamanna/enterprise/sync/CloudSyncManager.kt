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
import com.tamanna.enterprise.security.SecurityStorage

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

    // অ্যাডমিন হলে নিজের ফোল্ডার, আর পার্টনার হলে appConfig/admin থেকে অ্যাডমিনের UID বের করে সেই ফোল্ডার টার্গেট করবে
    private fun getTargetSyncDoc(context: Context, onReady: (DocumentReference?, Boolean) -> Unit) {
        val currentUser = auth().currentUser
        if (currentUser == null) {
            onReady(null, false)
            return
        }

        val isAdmin = SecurityStorage.canWrite(context)
        if (isAdmin) {
            val ref = db().collection("users").document(currentUser.uid).collection("data").document("cloud_backup_sync")
            onReady(ref, true) // true মানে অ্যাডমিন আপলোড (Push) করতে পারবে
        } else {
            db().collection("appConfig").document("admin").get()
                .addOnSuccessListener { doc ->
                    val adminUid = doc.getString("uid")
                    if (!adminUid.isNullOrBlank()) {
                        val ref = db().collection("users").document(adminUid).collection("data").document("cloud_backup_sync")
                        onReady(ref, false) // false মানে পার্টনার শুধুমাত্র রিড বা পুল (Pull) করবে, রাইট নয়
                    } else {
                        onReady(null, false)
                    }
                }
                .addOnFailureListener {
                    onReady(null, false)
                }
        }
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
        
        getTargetSyncDoc(appContext) { metaRef, canPush ->
            if (metaRef == null) {
                if (!isAuto) onComplete("অ্যাডমিন অ্যাকাউন্ট বা পাথ পাওয়া যায়নি।")
                return@getTargetSyncDoc
            }

            val currentLocalHash = calculateLocalHash(appContext)
            val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""

            metaRef.get().addOnSuccessListener { doc ->
                val cloudHash = doc.getString("dataHash") ?: ""

                when {
                    // ক্লাউডে ব্যাকআপ থাকলে এবং লোকাল হ্যাশের সাথে না মিললে যে কেউ তা নামিয়ে নিতে পারবে (Pull)
                    cloudHash.isNotBlank() && cloudHash != lastSyncedHash -> {
                        pullAllFromCloud(appContext, metaRef, cloudHash) { success ->
                            if (!isAuto) onComplete(if (success) "ক্লাউড থেকে নতুন ডেটা সিঙ্ক হয়েছে। ☁️⬇️" else "সিঙ্ক ব্যর্থ হয়েছে।")
                        }
                    }
                    // শুধুমাত্র অ্যাডমিন হলে নতুন ডেটা ক্লাউডে পাঠাতে পারবে (Push)
                    canPush && currentLocalHash != lastSyncedHash -> {
                        pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                            if (!isAuto) onComplete(if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "ক্লাউডে সেভ ব্যর্থ হয়েছে।")
                        }
                    }
                    else -> {
                        if (!isAuto) onComplete("সব ডেটা আপ-টু-데이트 আছে। ✅")
                    }
                }
            }.addOnFailureListener {
                if (canPush) {
                    pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                        if (!isAuto) onComplete(if (success) "প্রাথমিক ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "সিঙ্ক ব্যর্থ হয়েছে।")
                    }
                } else {
                    if (!isAuto) onComplete("ক্লাউডে এখনো কোনো ব্যাকআপ নেই।")
                }
            }
        }
    }

    private fun pushAllToCloud(context: Context, currentHash: String, metaRef: DocumentReference, onComplete: (Boolean) -> Unit) {
        val batch = db().batch()
        namespaces.forEach { namespace ->
            val values = toFirestoreMap(BusinessStorage.prefs(context, namespace).all)
            batch.set(metaRef.collection("namespaces").document(namespace), mapOf("values" to values), SetOptions.merge())
        }
        batch.set(metaRef, mapOf("dataHash" to currentHash, "updatedBy" to auth().currentUser?.uid), SetOptions.merge())

        batch.commit().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).edit().putString(KEY_LAST_LOCAL_HASH, currentHash).apply()
                onComplete(true)
            } else onComplete(false)
        }
    }

    private fun pullAllFromCloud(context: Context, metaRef: DocumentReference, newCloudHash: String, onComplete: (Boolean) -> Unit) {
        val refs = namespaces.map { metaRef.collection("namespaces").document(it).get() }
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
                smartSync(appContext, isAuto = true) {}
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

        getTargetSyncDoc(appContext) { metaRef, _ ->
            if (metaRef != null) {
                cloudListener = metaRef.addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    
                    val cloudHash = snapshot.getString("dataHash") ?: ""
                    val lastHash = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE).getString(KEY_LAST_LOCAL_HASH, "")
                    
                    if (cloudHash.isNotBlank() && cloudHash != lastHash) {
                        smartSync(appContext, isAuto = true) {}
                    }
                }
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
