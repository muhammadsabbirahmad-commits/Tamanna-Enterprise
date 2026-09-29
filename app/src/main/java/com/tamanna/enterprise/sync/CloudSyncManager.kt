package com.tamanna.enterprise.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
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

    private val mainHandler = Handler(Looper.getMainLooper())
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
                mainHandler.postDelayed(this, intervalMillis)
            }
        }.also {
            mainHandler.post(it)
        }
    }

    fun stopAutoSync() {
        isAutoSyncRunning = false
        autoSyncRunnable?.let { mainHandler.removeCallbacks(it) }
        autoSyncRunnable = null
    }

    fun startRealtimeSync(context: Context) {
        if (realtimeListener != null || realtimeListenerStarting) return
        realtimeListenerStarting = true

        getTargetSyncDoc(context) { ref, _ ->
            if (ref == null) {
                realtimeListenerStarting = false
                return@getTargetSyncDoc
            }

            realtimeListener = ref.addSnapshotListener { snapshot, error ->
                realtimeListenerStarting = false
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val cloudHash = snapshot.getString("dataHash") ?: return@addSnapshotListener
                val syncPrefs = context.applicationContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""

                if (cloudHash.isNotBlank() && cloudHash != lastSyncedHash) {
                    pullAllFromCloud(context.applicationContext, ref, cloudHash) { success ->
                        if (success) Log.d(TAG, "Local data updated from cloud via realtime sync.")
                    }
                }
            }
        }
    }

    fun stopRealtimeSync() {
        realtimeListener?.remove()
        realtimeListener = null
        realtimeListenerStarting = false
    }

    fun getTargetSyncDoc(context: Context, onResult: (DocumentReference?, Boolean) -> Unit) {
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) {
            onResult(null, false)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val localUser = SecurityStorage.getCurrentUser(context)
        val businessAccount = BusinessAccountStorage.get(context)
        val isPartner = localUser?.role == "PARTNER"

        if (isPartner) {
            val ownerUid = businessAccount.ownerUid.ifBlank { businessAccount.businessId }
            if (ownerUid.isBlank()) {
                onResult(null, false)
                return
            }
            val ref = db.collection("users").document(ownerUid).collection("data").document("backup")
            onResult(ref, false)
        } else {
            val ref = db.collection("users").document(authUser.uid).collection("data").document("backup")
            onResult(ref, true)
        }
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

    private fun isPartnerUser(context: Context): Boolean {
        val localUser = SecurityStorage.getCurrentUser(context)
        return localUser?.role == "PARTNER"
    }

    /**
     * মূল সিঙ্ক মেথড (আপনার অ্যাপের যেকোনো জায়গা থেকে কল হলে নির্দ্বিধায় কাজ করবে)
     */
    fun smartSync(context: Context, isAuto: Boolean = false, onComplete: (String, Boolean) -> Unit) {
        val appContext = context.applicationContext

        fun safeReturn(msg: String, success: Boolean) {
            mainHandler.post { onComplete(msg, success) }
        }

        try {
            getTargetSyncDoc(appContext) { metaRef, canPush ->
                if (metaRef == null) {
                    if (!isAuto) safeReturn("অ্যাকাউন্টের তথ্য পাওয়া যায়নি। পুনরায় লগইন করুন।", false)
                    return@getTargetSyncDoc
                }

                val currentLocalHash = calculateLocalHash(appContext)
                val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""
                
                // অ্যাপের লোকাল ডাটা ফাঁকা কিনা পরীক্ষা
                val isLocalEmpty = namespaces.all { ns -> BusinessStorage.prefs(appContext, ns).all.isEmpty() }

                metaRef.get().addOnSuccessListener { doc ->
                    val cloudHash = doc.getString("dataHash") ?: ""

                    if (isPartnerUser(appContext) || !canPush) {
                        // ==================== পার্টনার (PULL ONLY) ====================
                        if (doc.exists()) {
                            pullAllFromCloud(appContext, metaRef, cloudHash) { success ->
                                if (!isAuto) {
                                    val msg = if (success) "মালিকের ক্লাউড থেকে আপডেট নেওয়া হয়েছে! ✅" else "সিঙ্ক সম্পূর্ণ হতে পারেনি।"
                                    safeReturn(msg, success)
                                }
                            }
                        } else {
                            if (!isAuto) safeReturn("মালিকের ক্লাউডে কোনো তথ্য পাওয়া যায়নি।", false)
                        }
                    } else {
                        // ==================== ওনার (PUSH OR RESTORE) ====================
                        if (isLocalEmpty && doc.exists() && cloudHash.isNotBlank()) {
                            // অ্যাপ ক্লিয়ার করা হলে এবং ক্লাউডে ডাটা থাকলে আগে রিস্টোর হবে
                            pullAllFromCloud(appContext, metaRef, cloudHash) { success ->
                                if (!isAuto) {
                                    val msg = if (success) "ক্লাউড থেকে পুরনো ডেটা রিস্টোর হয়েছে! ☁️⬇️" else "রিস্টোর ব্যর্থ হয়েছে।"
                                    safeReturn(msg, success)
                                }
                            }
                        } else {
                            // লোকাল ডাটা থাকলে ক্লাউডে সেভ হবে
                            pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                                if (!isAuto) {
                                    val msg = if (success) "সফলভাবে ক্লাউডে সেভ হয়েছে! ☁️" else "ক্লাউডে সেভ করতে ব্যর্থ হয়েছে।"
                                    safeReturn(msg, success)
                                }
                            }
                        }
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Sync fetch error", e)
                    if (!isPartnerUser(appContext) && canPush && !isLocalEmpty) {
                        pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                            if (!isAuto) {
                                val msg = if (success) "ক্লাউডে সেভ হয়েছে! ☁️" else "সিঙ্ক করতে ব্যর্থ হয়েছে।"
                                safeReturn(msg, success)
                            }
                        }
                    } else {
                        if (!isAuto) safeReturn("ইন্টারনেট সমস্যা বা ফায়ারবেসে কানেক্ট করা যাচ্ছে না।", false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "smartSync exception", e)
            if (!isAuto) safeReturn("সিঙ্ক করতে সমস্যা হয়েছে।", false)
        }
    }

    /**
     * সাপোর্ট মেথড (যদি অ্যাপের কোডে কোথাও simpleSync ডাকা হয়ে থাকে)
     */
    fun simpleSync(context: Context, onComplete: (String, Boolean) -> Unit) {
        smartSync(context, isAuto = false, onComplete = onComplete)
    }

    fun pushAllToCloud(context: Context, currentLocalHash: String, metaRef: DocumentReference, onResult: (Boolean) -> Unit) {
        try {
            val dataMap = mutableMapOf<String, Any>()

            for (ns in namespaces) {
                val prefs = BusinessStorage.prefs(context, ns)
                val nsMap = mutableMapOf<String, Any>()
                for ((k, v) in prefs.all) {
                    if (k != null && v != null) {
                        if (v is Set<*>) {
                            nsMap[k] = v.filterNotNull().toList()
                        } else {
                            nsMap[k] = v
                        }
                    }
                }
                dataMap[ns] = nsMap
            }
            dataMap["dataHash"] = currentLocalHash
            dataMap["updatedAt"] = System.currentTimeMillis()

            metaRef.set(dataMap)
                .addOnSuccessListener {
                    context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                        .edit().putString(KEY_LAST_LOCAL_HASH, currentLocalHash).apply()
                    mainHandler.post { onResult(true) }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Push failed", e)
                    mainHandler.post { onResult(false) }
                }
        } catch (e: Exception) {
            Log.e(TAG, "pushAllToCloud exception", e)
            mainHandler.post { onResult(false) }
        }
    }

    fun pullAllFromCloud(context: Context, metaRef: DocumentReference, cloudHash: String, onResult: (Boolean) -> Unit) {
        metaRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    try {
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
                                            is List<*> -> {
                                                val stringSet = v.filterIsInstance<String>().toSet()
                                                editor.putStringSet(k, stringSet)
                                            }
                                        }
                                    }
                                }
                                editor.apply()
                            }
                        }
                        context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                            .edit().putString(KEY_LAST_LOCAL_HASH, cloudHash).apply()
                        mainHandler.post { onResult(true) }
                    } catch (e: Exception) {
                        Log.e(TAG, "pullAllFromCloud exception", e)
                        mainHandler.post { onResult(false) }
                    }
                } else {
                    mainHandler.post { onResult(false) }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Pull failed", e)
                mainHandler.post { onResult(false) }
            }
    }
}
