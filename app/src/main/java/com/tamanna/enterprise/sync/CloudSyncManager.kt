package com.tamanna.enterprise.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.security.MessageDigest

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    private const val PREF_SYNC = "tamanna_sync_prefs"
    private const val KEY_LAST_LOCAL_HASH = "last_local_hash"

    // অ্যাপের সমস্ত লোকাল ডেটা স্টোরেজ বা নেমস্পেসগুলোর তালিকা
    private val namespaces = listOf(
        "tamanna_enterprise_settings",
        "tamanna_business_prefs",
        "tamanna_inventory_prefs",
        "tamanna_customers_prefs",
        "tamanna_transactions_prefs"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var autoSyncRunnable: Runnable? = null
    private var isAutoSyncRunning = false

    fun isAutoSyncEnabled(): Boolean = isAutoSyncRunning

    // অটো-সিঙ্ক চালু করার ফাংশন
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

    // অটো-সিঙ্ক বন্ধ করার ফাংশন
    fun stopAutoSync() {
        isAutoSyncRunning = false
        autoSyncRunnable?.let { handler.removeCallbacks(it) }
        autoSyncRunnable = null
        Log.d(TAG, "Auto-sync stopped.")
    }

    // অ্যাডমিন বা পার্টনারের জন্য ফায়ারস্টোর সঠিক পাথ বা ডকুমেন্ট রেফারেন্স বের করা
    fun getTargetSyncDoc(context: Context, onResult: (DocumentReference?, Boolean) -> Unit) {
        val currentUser = SecurityStorage.getCurrentUser(context)
        val authUser = FirebaseAuth.getInstance().currentUser

        if (authUser == null) {
            onResult(null, false)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val isAdmin = currentUser?.role == SecurityStorage.ROLE_ADMIN || authUser.email == "sathiravoice9@gmail.com"

        if (isAdmin) {
            val adminRef = db.collection("backups").document(authUser.uid)
            // অ্যাডমিনের ইউআইডি appConfig/admin-এ সেভ করে রাখা যাতে পার্টনারা সঠিক পাথ পায়
            db.collection("appConfig").document("admin")
                .set(mapOf("adminUid" to authUser.uid, "email" to (authUser.email ?: "")))
            onResult(adminRef, true)
        } else {
            // পার্টনার হলে appConfig/admin থেকে মূল অ্যাডমিনের UID এনে সিঙ্ক পাথ ঠিক করা
            db.collection("appConfig").document("admin").get()
                .addOnSuccessListener { doc ->
                    val adminUid = doc.getString("adminUid")
                    if (!adminUid.isNullOrBlank()) {
                        val partnerRef = db.collection("backups").document(adminUid)
                        onResult(partnerRef, false)
                    } else {
                        onResult(db.collection("backups").document(authUser.uid), false)
                    }
                }
                .addOnFailureListener {
                    onResult(db.collection("backups").document(authUser.uid), false)
                }
        }
    }

    // লোকাল ডেটার হাশ বা স্বাক্ষর হিসাব করার ফাংশন
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

    // মূল স্মার্ট সিঙ্ক ফাংশন (লোকাল ফাঁকা থাকলে ফোর্স পুল করে ডেটা ফিরিয়ে আনবে)
    fun smartSync(context: Context, isAuto: Boolean = false, onComplete: (String, Boolean) -> Unit) {
        val appContext = context.applicationContext

        getTargetSyncDoc(appContext) { metaRef, canPush ->
            if (metaRef == null) {
                if (!isAuto) onComplete("অ্যাডমিন অ্যাকাউন্ট বা পাথ পাওয়া যায়নি।", false)
                return@getTargetSyncDoc
            }

            val currentLocalHash = calculateLocalHash(appContext)
            val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""

            // চেক করা হচ্ছে লোকাল ফোনে কোনো ডেটা আছে কি না
            val isLocalEmpty = namespaces.all { ns -> BusinessStorage.prefs(appContext, ns).all.isEmpty() }

            metaRef.get().addOnSuccessListener { doc ->
                val cloudHash = doc.getString("dataHash") ?: ""

                when {
                    // লোকাল ফোন ফাঁকা থাকলে অথবা ক্লাউডে নতুন ডেটা থাকলে জোরপূর্বক (Force) ক্লাউড থেকে নামিয়ে আনবে
                    isLocalEmpty || (cloudHash.isNotBlank() && cloudHash != lastSyncedHash) -> {
                        val pullHash = if (cloudHash.isBlank()) "forced_sync" else cloudHash
                        pullAllFromCloud(appContext, metaRef, pullHash) { success ->
                            if (!isAuto) {
                                val msg = if (success) "ক্লাউড থেকে পুরনো ডেটা সফলভাবে ফিরিয়ে আনা হয়েছে! ☁️⬇️" else "সিঙ্ক ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    // শুধুমাত্র অ্যাডমিন হলে এবং লোকাল ডেটা পরিবর্তিত হলে ক্লাউডে পাঠাবে (Push)
                    canPush && currentLocalHash != lastSyncedHash -> {
                        pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                            if (!isAuto) {
                                val msg = if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "ক্লাউডে সেভ ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    else -> {
                        if (!isAuto) onComplete("সব ডেটা আপ-টু-데이트 আছে। ✅", false)
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

    // ক্লাউডে ডেটা আপলোড করা (Push)
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

    // ক্লাউড থেকে ডেটা নামিয়ে আনা ও লোকাল স্টোরেজে সংরক্ষণ করা (Pull)
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
                                        is Float -> editor.putFloat(k, v)
                                        is Boolean -> editor.putBoolean(k, v)
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
