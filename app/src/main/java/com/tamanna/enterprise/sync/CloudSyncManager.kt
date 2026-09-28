package com.tamanna.enterprise.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.tamanna.enterprise.business.BusinessStorage
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
     * ইউজারের নিজস্ব রিয়েলটাইম সিঙ্ক।
     * ইউজার যদি অন্য কোনো ডিভাইসে ডেটা আপডেট করে, তবে এই লিসেনার দিয়ে তা স্বয়ংক্রিয়ভাবে আপডেট হবে।
     */
    fun startRealtimeSync(context: Context) {
        val authUser = FirebaseAuth.getInstance().currentUser ?: return
        if (realtimeListener != null || realtimeListenerStarting) return

        realtimeListenerStarting = true
        val db = FirebaseFirestore.getInstance()
        
        // ইউজারের নিজস্ব ব্যাকআপ পাথ
        val ref = db.collection("users")
            .document(authUser.uid)
            .collection("data")
            .document("backup")

        realtimeListener = ref.addSnapshotListener { snapshot, error ->
            realtimeListenerStarting = false
            if (error != null) {
                Log.w(TAG, "Realtime sync listener failed.", error)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val cloudHash = snapshot.getString("dataHash") ?: return@addSnapshotListener
            val syncPrefs = context.applicationContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""

            if (cloudHash.isBlank() || cloudHash == lastSyncedHash) return@addSnapshotListener

            pullAllFromCloud(context.applicationContext, ref, cloudHash) { success ->
                if (success) Log.d(TAG, "Local data updated from user's personal cloud backup.")
            }
        }
        Log.d(TAG, "Realtime sync started for userId=${authUser.uid}")
    }

    fun stopRealtimeSync() {
        realtimeListener?.remove()
        realtimeListener = null
        realtimeListenerStarting = false
        Log.d(TAG, "Realtime sync stopped.")
    }

    /**
     * সরাসরি ইউজারের নিজস্ব ফোল্ডার টার্গেট করার ফাংশন
     */
    fun getTargetSyncDoc(context: Context, onResult: (DocumentReference?, Boolean) -> Unit) {
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) {
            onResult(null, false)
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        // বিজনেস আইডির বদলে সরাসরি ইউজারের ফায়ারস্টোর পাথ ব্যবহার করা হচ্ছে
        val sharedRef = db.collection("users")
            .document(authUser.uid)
            .collection("data")
            .document("backup")

        // ইউজার যেহেতু তার নিজের ডেটা নিজেই সেভ করছে, তাই সব সময় True (canPush) রিটার্ন হবে
        onResult(sharedRef, true)
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
                if (!isAuto) onComplete("অ্যাকাউন্ট বা পাথ পাওয়া যায়নি।", false)
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
                        pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                            if (!isAuto) {
                                val msg = if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "ক্লাউডে সেভ ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    isLocalEmpty || (cloudHash.isNotBlank() && cloudHash != lastSyncedHash) -> {
                        val pullHash = if (cloudHash.isBlank()) "forced_sync" else cloudHash
                        pullAllFromCloud(appContext, metaRef, pullHash) { success ->
                            if (!isAuto) {
                                val msg = if (success) "ক্লাউড থেকে পুরনো ডেটা সফলভাবে ফিরিয়ে আনা হয়েছে! ☁️⬇️" else "সিঙ্ক ব্যর্থ হয়েছে।"
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
                    if (!isAuto) onComplete("ক্লাউডে এখনো কোনো ব্যাকআপ নেই।", false)
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
                            editor.clear()
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
