package com.tamanna.enterprise.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.security.MessageDigest

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    private const val PREF_SYNC = "tamanna_sync_prefs"
    private const val KEY_LAST_LOCAL_HASH = "last_local_hash"

    private val namespaces = listOf(
        "tamanna_enterprise_settings",
        "tamanna_business_prefs",
        "tamanna_inventory_prefs",
        "tamanna_customers_prefs",
        "tamanna_transactions_prefs",
        "tamanna_enterprise_products"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var autoSyncRunnable: Runnable? = null
    private var isAutoSyncRunning = false
    private var realtimeListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var realtimeListenerStarting = false

    fun isAutoSyncEnabled(): Boolean = isAutoSyncRunning

    fun startAutoSync(context: Context, intervalMillis: Long = 30000L) {
        if (isAutoSyncRunning) return
        isAutoSyncRunning = true
        
        autoSyncRunnable = object : Runnable {
            override fun run() {
                smartSync(context, isAuto = true) { _, _ -> }
                handler.postDelayed(this, intervalMillis)
            }
        }.also {
            handler.post(it)
        }
        Log.d(TAG, "Auto-sync started.")
    }

    fun stopAutoSync() {
        isAutoSyncRunning = false
        autoSyncRunnable?.let { handler.removeCallbacks(it) }
        autoSyncRunnable = null
        Log.d(TAG, "Auto-sync stopped.")
    }

    /**
     * পার্টনারদের জন্য রিয়েল-টাইম সিঙ্ক: 
     * মালিক ডেটা পরিবর্তন করলেই পার্টনারের অ্যাপে অটোমেটিক রিসিভ (Pull) হবে।
     */
    fun startRealtimeSync(context: Context) {
        val authUser = FirebaseAuth.getInstance().currentUser ?: return
        val businessId = BusinessAccountStorage.get(context).businessId.trim()
        if (businessId.isBlank() || realtimeListener != null || realtimeListenerStarting) return

        realtimeListenerStarting = true
        val db = FirebaseFirestore.getInstance()
        
        // শেয়ার্ড বিজনেস ব্যাকআপ পাথ
        val ref = db.collection("businesses")
            .document(businessId)
            .collection("data")
            .document("backup")

        val localUser = SecurityStorage.getCurrentUser(context)
        // শুধুমাত্র পার্টনারদের জন্য রিয়েলটাইম লিসেনার চালু হবে (মালিক তো নিজেই পুশ করছে)
        if (localUser?.role == "PARTNER") {
            realtimeListener = ref.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Partner realtime sync listener failed.", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val cloudHash = snapshot.getString("dataHash") ?: return@addSnapshotListener
                val syncPrefs = context.applicationContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""

                if (cloudHash.isBlank() || cloudHash == lastSyncedHash) return@addSnapshotListener

                // মালিকের ব্যাকআপ থেকে পার্টনারের ফোনে ডেটা ডাউনলোড (Pull) করা হচ্ছে
                pullAllFromCloud(context.applicationContext, ref, cloudHash) { success ->
                    if (success) Log.d(TAG, "Partner data updated from Owner cloud backup.")
                }
            }
            Log.d(TAG, "Partner realtime sync started for businessId=$businessId")
        } else {
            realtimeListenerStarting = false
        }
    }

    fun stopRealtimeSync() {
        realtimeListener?.remove()
        realtimeListener = null
        realtimeListenerStarting = false
        Log.d(TAG, "Partner realtime sync stopped.")
    }

    fun getTargetSyncDoc(context: Context, onResult: (DocumentReference?, Boolean) -> Unit) {
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) {
            onResult(null, false)
            return
        }

        val businessId = BusinessAccountStorage.get(context).businessId.trim()
        if (businessId.isBlank()) {
            onResult(null, false)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val sharedRef = db.collection("businesses")
            .document(businessId)
            .collection("data")
            .document("backup")

        val localUser = SecurityStorage.getCurrentUser(context)
        
        // যদি ইউজার OWNER বা ADMIN হয়, তবে canPush = true (সে আপলোড করতে পারবে)
        // যদি ইউজার PARTNER হয়, তবে canPush = false (সে শুধু ডাউনলোড করতে পারবে)
        val isOwner = localUser?.role == "OWNER" || localUser?.role == SecurityStorage.ROLE_ADMIN
        
        onResult(sharedRef, isOwner)
    }

    fun calculateLocalHash(context: Context): String {
        val sb = StringBuilder()
        for (ns in namespaces) {
            val prefs = BusinessStorage.prefs(context, ns)
            val sortedMap = prefs.all.entries.sortedBy { it.key }
            for ((k, v) in sortedMap) {
                sb.append("$k=$v;")
            }
        }
        return md5(sb.toString())
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun smartSync(context: Context, isAuto: Boolean = false, onComplete: (String, Boolean) -> Unit) {
        val appContext = context.applicationContext

        getTargetSyncDoc(appContext) { metaRef, canPush ->
            if (metaRef == null) {
                if (!isAuto) onComplete("বিজনেস অ্যাকাউন্ট বা পাথ পাওয়া যায়নি।", false)
                return@getTargetSyncDoc
            }

            val currentLocalHash = calculateLocalHash(appContext)
            val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""
            val isLocalEmpty = namespaces.all { ns -> BusinessStorage.prefs(appContext, ns).all.isEmpty() }

            metaRef.get().addOnSuccessListener { doc ->
                val cloudHash = doc.getString("dataHash") ?: ""

                when {
                    canPush && currentLocalHash != lastSyncedHash -> {
                        // শুধুমাত্র OWNER ডেটা আপলোড (Push) করবে
                        pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                            if (!isAuto) {
                                val msg = if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "ক্লাউডে সেভ ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    isLocalEmpty || (cloudHash.isNotBlank() && cloudHash != lastSyncedHash) -> {
                        // PARTNER সব সময় ডেটা ডাউনলোড (Pull) করবে (এমনকি OWNER-ও অন্য ডিভাইস থেকে সিঙ্ক করলে Pull করবে)
                        val pullHash = if (cloudHash.isBlank()) "forced_sync" else cloudHash
                        pullAllFromCloud(appContext, metaRef, pullHash) { success ->
                            if (!isAuto) {
                                val msg = if (success) "ক্লাউড থেকে সর্বশেষ আপডেট পাওয়া গেছে! ☁️⬇️" else "সিঙ্ক ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    else -> {
                        if (!isAuto) onComplete("সব ডেটা আপ-টু-ডেট আছে। ✅", false)
                    }
                }
            }.addOnFailureListener {
                if (canPush) {
                    pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                        if (!isAuto) {
                            val msg = if (success) "প্রাথমিক ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "সিঙ্ক ব্যর্থ হয়েছে।"
                            onComplete(msg, success)
                        }
                    }
                } else {
                    if (!isAuto) onComplete("মালিক এখনো কোনো ডেটা আপলোড করেননি।", false)
                }
            }
        }
    }

    fun pushAllToCloud(context: Context, currentLocalHash: String, metaRef: DocumentReference, onResult: (Boolean) -> Unit) {
        val dataMap = mutableMapOf<String, Any>()
        for (ns in namespaces) {
            val prefs = BusinessStorage.prefs(context, ns)
            dataMap[ns] = prefs.all
        }
        dataMap["dataHash"] = currentLocalHash
        dataMap["updatedAt"] = System.currentTimeMillis()

        metaRef.set(dataMap)
            .addOnSuccessListener {
                val syncPrefs = context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                syncPrefs.edit().putString(KEY_LAST_LOCAL_HASH, currentLocalHash).apply()
                onResult(true)
            }
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun pullAllFromCloud(context: Context, metaRef: DocumentReference, cloudHash: String, onResult: (Boolean) -> Unit) {
        metaRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    for (ns in namespaces) {
                        val cloudData = doc.get(ns) as? Map<*, *>
                        if (cloudData != null) {
                            val prefs = BusinessStorage.prefs(context, ns)
                            val editor = prefs.edit()
                            editor.clear() // আগের সব লোকাল ডেটা মুছে মালিকের ফ্রেশ ডেটা বসানো হচ্ছে
                            for ((k, v) in cloudData) {
                                if (k is String && v != null) {
                                    when (v) {
                                        is String -> editor.putString(k, v)
                                        is Int -> editor.putInt(k, v)
                                        is Long -> editor.putLong(k, v)
                                        is Double -> editor.putFloat(k, v.toFloat())
                                        is Float -> editor.putFloat(k, v)
                                        is Boolean -> editor.putBoolean(k, v)
                                        is Number -> editor.putLong(k, v.toLong())
                                    }
                                }
                            }
                            editor.apply()
                        }
                    }
                    val syncPrefs = context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                    syncPrefs.edit().putString(KEY_LAST_LOCAL_HASH, cloudHash).apply()
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
            .addOnFailureListener {
                onResult(false)
            }
    }
}
