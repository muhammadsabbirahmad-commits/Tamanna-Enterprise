package com.tamanna.enterprise.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.tamanna.enterprise.business.BusinessAccountStorage
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

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
    }

    fun stopAutoSync() {
        isAutoSyncRunning = false
        autoSyncRunnable?.let { handler.removeCallbacks(it) }
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

    fun smartSync(context: Context, isAuto: Boolean = false, onComplete: (String, Boolean) -> Unit) {
        val appContext = context.applicationContext
        val mainThreadHandler = Handler(Looper.getMainLooper())
        val isHandled = AtomicBoolean(false)

        fun sendResult(msg: String, success: Boolean) {
            if (isHandled.compareAndSet(false, true)) {
                mainThreadHandler.post { onComplete(msg, success) }
            }
        }

        // ৫ সেকেন্ডের টাইমআউট প্রটেকশন (যাতে কখনোই স্ক্রিন ফ্রিজ হয়ে না থাকে)
        val timeoutRunnable = Runnable {
            if (!isAuto) {
                sendResult("নেটওয়ার্ক স্লো বা সময় বেশি নিচ্ছে। লোকাল ডাটা দিয়ে চালানো হচ্ছে।", true)
            } else {
                sendResult("", false)
            }
        }
        mainThreadHandler.postDelayed(timeoutRunnable, 5000L)

        getTargetSyncDoc(appContext) { metaRef, canPush ->
            if (metaRef == null) {
                mainThreadHandler.removeCallbacks(timeoutRunnable)
                if (!isAuto) sendResult("অ্যাকাউন্টের তথ্য পাওয়া যায়নি।", false)
                return@getTargetSyncDoc
            }

            val currentLocalHash = calculateLocalHash(appContext)
            val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""
            val isLocalEmpty = namespaces.all { ns -> BusinessStorage.prefs(appContext, ns).all.isEmpty() }

            metaRef.get().addOnSuccessListener { doc ->
                mainThreadHandler.removeCallbacks(timeoutRunnable)
                val cloudHash = doc.getString("dataHash") ?: ""

                when {
                    canPush -> {
                        if (isLocalEmpty && cloudHash.isNotBlank()) {
                            pullAllFromCloud(appContext, metaRef, cloudHash) { success ->
                                if (!isAuto) {
                                    val msg = if (success) "ক্লাউড থেকে পুরনো ডেটা রিস্টোর হয়েছে! ☁️⬇️" else "রিস্টোর সফল হয়েছে।"
                                    sendResult(msg, success)
                                }
                            }
                        } else if (currentLocalHash != lastSyncedHash || !isAuto) {
                            pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                                if (!isAuto) {
                                    val msg = if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "সেভ সম্পন্ন হয়েছে।"
                                    sendResult(msg, success)
                                }
                            }
                        } else {
                            if (!isAuto) sendResult("সব ডেটা আপ-টু-ডেট আছে। ✅", true)
                        }
                    }
                    !canPush -> {
                        if (isLocalEmpty || (cloudHash.isNotBlank() && cloudHash != lastSyncedHash) || !isAuto) {
                            val pullHash = if (cloudHash.isBlank()) "forced_sync" else cloudHash
                            pullAllFromCloud(appContext, metaRef, pullHash) { success ->
                                if (!isAuto) {
                                    val msg = if (success) "মালিকের ক্লাউড থেকে ডেটা সিঙ্ক হয়েছে! ☁️⬇️" else "সিঙ্ক সম্পন্ন হয়েছে।"
                                    sendResult(msg, success)
                                }
                            }
                        } else {
                            if (!isAuto) sendResult("সব ডেটা আপ-টু-ডেট আছে। ✅", true)
                        }
                    }
                }
            }.addOnFailureListener { e ->
                mainThreadHandler.removeCallbacks(timeoutRunnable)
                Log.e(TAG, "Sync check failed", e)
                if (canPush && !isLocalEmpty) {
                    pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                        if (!isAuto) {
                            val msg = if (success) "প্রাথমিক ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "সেভ সম্পন্ন হয়েছে।"
                            sendResult(msg, success)
                        }
                    }
                } else {
                    if (!isAuto) sendResult("ক্লাউডের সাথে কানেক্ট করা যাচ্ছে না।", false)
                }
            }
        }
    }

    fun pushAllToCloud(context: Context, currentLocalHash: String, metaRef: DocumentReference, onResult: (Boolean) -> Unit) {
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
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Push failed", e)
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
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Pull failed", e)
                onResult(false)
            }
    }
}
