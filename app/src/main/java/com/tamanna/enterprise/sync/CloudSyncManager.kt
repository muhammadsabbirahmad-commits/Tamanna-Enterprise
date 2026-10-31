package com.tamanna.enterprise.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentReference
import com.tamanna.enterprise.business.BusinessStorage
import com.tamanna.enterprise.product.ProductStorage
import com.tamanna.enterprise.security.SecurityStorage
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    private const val PREF_SYNC = "tamanna_sync_prefs"
    private const val KEY_LAST_LOCAL_HASH = "last_local_hash"

    // বিক্রয় (sales) সহ সব প্রয়োজনীয় নেমস্পেস
    private val namespaces = listOf(
        "tamanna_enterprise_settings",
        "tamanna_business_prefs",
        "tamanna_enterprise_finance", // প্রতিষ্ঠানের খরচ ও ড্যামেজ
        "tamanna_partner_storage", // অংশীদার ও লাভের শতাংশ
        "tamanna_inventory_prefs",
        "tamanna_customers_prefs",
        "tamanna_transactions_prefs",
        "tamanna_enterprise_products",
        "tamanna_enterprise_purchases",
        "tamanna_enterprise_sales", // <-- বিক্রির ডাটা ক্লাউডে সিঙ্ক হওয়ার জন্য যুক্ত করা হয়েছে
        "tamanna_supplier_due",
        "tamanna_enterprise_sale_returns",
        "tamanna_enterprise_sale_transactions",
        "tamanna_enterprise_activity_log",
        "tamanna_inventory_meta"
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
        FirebaseAuth.getInstance().currentUser ?: return
        if (realtimeListener != null || realtimeListenerStarting) return

        realtimeListenerStarting = true

        getTargetSyncDoc(context) { ref, _ ->
            if (ref == null) {
                realtimeListenerStarting = false
                return@getTargetSyncDoc
            }

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
                    if (success) Log.d(TAG, "Local data updated from cloud backup.")
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

        val businessId = BusinessStorage.getActiveBusinessId(context).trim()
        if (businessId.isBlank()) {
            onResult(null, false)
            return
        }

        val isOwner = SecurityStorage.getCurrentUser(context)?.role == SecurityStorage.ROLE_ADMIN
        val ref = FirebaseFirestore.getInstance()
            .collection("businesses")
            .document(businessId)
            .collection("data")
            .document("backup")

        onResult(ref, isOwner)
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
                if (!isAuto) onComplete("লগইন তথ্য পাওয়া যায়নি।", false)
                return@getTargetSyncDoc
            }

            val currentLocalHash = calculateLocalHash(appContext)
            val syncPrefs = appContext.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val lastSyncedHash = syncPrefs.getString(KEY_LAST_LOCAL_HASH, "") ?: ""
            
            val isProductsEmpty = ProductStorage.getProducts(appContext).isEmpty()

            metaRef.get().addOnSuccessListener { doc ->
                val cloudHash = doc.getString("dataHash") ?: ""

                when {
                    cloudHash.isNotBlank() && (isProductsEmpty || lastSyncedHash.isBlank()) -> {
                        pullAllFromCloud(appContext, metaRef, cloudHash) { success ->
                            if (!isAuto) {
                                val msg = if (success) "ক্লাউড থেকে ডেটা রিস্টোর হয়েছে! ☁️⬇️" else "রিস্টোর ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    canPush && currentLocalHash != lastSyncedHash -> {
                        pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                            if (!isAuto) {
                                val msg = if (success) "নতুন ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "ক্লাউডে সেভ ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    cloudHash.isNotBlank() && cloudHash != lastSyncedHash -> {
                        pullAllFromCloud(appContext, metaRef, cloudHash) { success ->
                            if (!isAuto) {
                                val msg = if (success) "ক্লাউড থেকে আপডেট সিঙ্ক হয়েছে! ☁️⬇️" else "সিঙ্ক ব্যর্থ হয়েছে।"
                                onComplete(msg, success)
                            }
                        }
                    }
                    else -> {
                        if (!isAuto) onComplete("সব ডেটা আপ-টু-ডেট আছে। ✅", false)
                    }
                }
            }.addOnFailureListener {
                if (canPush && !isProductsEmpty) {
                    pushAllToCloud(appContext, currentLocalHash, metaRef) { success ->
                        if (!isAuto) {
                            val msg = if (success) "প্রাথমিক ডেটা ক্লাউডে সেভ হয়েছে। ☁️⬆️" else "সিঙ্ক ব্যর্থ হয়েছে।"
                            onComplete(msg, success)
                        }
                    }
                } else {
                    if (!isAuto) onComplete("ক্লাউডে কোনো ব্যাকআপ পাওয়া যায়নি।", false)
                }
            }
        }
    }

    fun pushAllToCloud(context: Context, currentLocalHash: String, metaRef: DocumentReference, onResult: (Boolean) -> Unit) {
        val authUser = FirebaseAuth.getInstance().currentUser ?: run {
            onResult(false)
            return
        }

        val businessId = BusinessStorage.getActiveBusinessId(context).trim()
        if (businessId.isBlank()) {
            onResult(false)
            return
        }

        // Before writing, read the existing cloud namespaces and merge the two
        // financial namespaces. This prevents one phone's new entries from
        // blindly replacing entries already uploaded by another phone.
        val backupDataRef = metaRef.collection("backupData")
        backupDataRef.get()
            .addOnSuccessListener { cloudSnapshot ->
                try {
                    val cloudByNamespace = cloudSnapshot.documents.associate { doc ->
                        doc.id to (doc.get("data") as? Map<*, *> ?: emptyMap<Any, Any>())
                    }

                    val db = FirebaseFirestore.getInstance()
                    val businessDocRef = db.collection("businesses").document(businessId)
                    val businessData = mapOf(
                        "ownerUid" to authUser.uid,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    val batch = db.batch()
                    batch.set(businessDocRef, businessData, com.google.firebase.firestore.SetOptions.merge())

                    for (ns in namespaces) {
                        val prefs = BusinessStorage.prefs(context, ns)
                        val localMap = mutableMapOf<String, Any>()
                        for ((k, v) in prefs.all) {
                            if (k != null && v != null) {
                                localMap[k] = if (v is Set<*>) v.filterNotNull().toList() else v
                            }
                        }

                        if (ns == "tamanna_enterprise_finance" || ns == "tamanna_partner_storage") {
                            val merged = mergeFinanceNamespace(ns, localMap, cloudByNamespace[ns].orEmpty())
                            val editor = prefs.edit()
                            for ((key, value) in merged) {
                                when (value) {
                                    is String -> editor.putString(key, value)
                                    is Int -> editor.putInt(key, value)
                                    is Long -> editor.putLong(key, value)
                                    is Double -> editor.putFloat(key, value.toFloat())
                                    is Float -> editor.putFloat(key, value)
                                    is Boolean -> editor.putBoolean(key, value)
                                    is List<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                                }
                            }
                            editor.apply()
                            localMap.clear()
                            localMap.putAll(merged)
                        }

                        val nsRef = metaRef.collection("backupData").document(ns)
                        batch.set(nsRef, mapOf("data" to localMap))
                    }

                    val finalHash = calculateLocalHash(context)
                    batch.set(metaRef, mapOf(
                        "dataHash" to finalHash,
                        "updatedAt" to System.currentTimeMillis(),
                        "businessId" to businessId,
                        "ownerUid" to authUser.uid,
                        "schemaVersion" to 2
                    ))
                    batch.commit()
                        .addOnSuccessListener {
                            val syncPrefs = context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                            syncPrefs.edit().putString(KEY_LAST_LOCAL_HASH, finalHash).apply()
                            onResult(true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Push failed while writing merged backup batch", e)
                            onResult(false)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not safely merge finance/partner backup data", e)
                    onResult(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Could not read existing cloud backup before push", e)
                // Do not fall back to a blind overwrite when cloud data cannot be read.
                onResult(false)
            }
    }

    private fun mergeFinanceNamespace(
        namespace: String,
        local: Map<String, Any>,
        cloud: Map<*, *>
    ): Map<String, Any> {
        val merged = LinkedHashMap<String, Any>()
        local.forEach { (key, value) -> merged[key] = value }
        cloud.forEach { (key, value) ->
            if (key is String && value != null && !merged.containsKey(key)) merged[key] = value
        }

        val arrayKeys = when (namespace) {
            "tamanna_enterprise_finance" -> listOf("expenses", "damages")
            "tamanna_partner_storage" -> listOf("partners")
            else -> emptyList()
        }
        for (key in arrayKeys) {
            val localRaw = local[key] as? String ?: "[]"
            val cloudRaw = cloud[key] as? String ?: "[]"
            merged[key] = mergeJsonArrayById(localRaw, cloudRaw)
        }
        return merged
    }

    // Local records are retained; cloud records with the same ID take
    // precedence so edits made on the owner phone propagate to the other phone.
    private fun mergeJsonArrayById(localRaw: String, cloudRaw: String): String {
        val records = LinkedHashMap<String, JSONObject>()
        fun addAll(raw: String) {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.opt("id")?.toString()?.takeIf { it.isNotBlank() } ?: continue
                records[id] = obj
            }
        }
        addAll(localRaw)
        addAll(cloudRaw)
        val result = JSONArray()
        records.values.forEach { result.put(it) }
        return result.toString()
    }

    fun pullAllFromCloud(context: Context, metaRef: DocumentReference, cloudHash: String, onResult: (Boolean) -> Unit) {
        metaRef.get()
            .addOnSuccessListener { metaDoc ->
                if (!metaDoc.exists()) {
                    onResult(false)
                    return@addOnSuccessListener
                }

                val backupDataRef = metaRef.collection("backupData")
                backupDataRef.get()
                    .addOnSuccessListener { backupSnapshot ->
                        try {
                            if (backupSnapshot.documents.isNotEmpty()) {
                                for (nsDoc in backupSnapshot.documents) {
                                    val ns = nsDoc.id
                                    if (ns !in namespaces) continue
                                    val cloudData = nsDoc.get("data") as? Map<*, *> ?: continue
                                    restoreNamespace(context, ns, cloudData)
                                }
                            } else {
                                for (ns in namespaces) {
                                    val cloudData = metaDoc.get(ns) as? Map<*, *> ?: continue
                                    restoreNamespace(context, ns, cloudData)
                                }
                            }

                            val syncPrefs = context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                            syncPrefs.edit().putString(KEY_LAST_LOCAL_HASH, cloudHash).apply()
                            onResult(true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during pullAllFromCloud processing", e)
                            onResult(false)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to read backup data", e)
                        onResult(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Pull failed", e)
                onResult(false)
            }
    }

    private fun restoreNamespace(context: Context, ns: String, cloudData: Map<*, *>) {
        val prefs = BusinessStorage.prefs(context, ns)
        val editor = prefs.edit()

        // Finance and partner data are merged by stable record ID; never clear
        // local records merely because a cloud snapshot is partial.
        if (ns == "tamanna_enterprise_finance" || ns == "tamanna_partner_storage") {
            val local = prefs.all.mapNotNull { (key, value) ->
                if (key is String && value != null) key to value else null
            }.toMap()
            val merged = mergeFinanceNamespace(ns, local, cloudData)
            for ((key, value) in merged) {
                when (value) {
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Number -> editor.putLong(key, value.toLong())
                    is List<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.apply()
            return
        }

        // Existing behavior for other namespaces remains unchanged in this focused fix.
        editor.clear()
        for ((k, v) in cloudData) {
            if (k is String && v != null) {
                try {
                    when (v) {
                        is String -> editor.putString(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Double -> editor.putFloat(k, v.toFloat())
                        is Float -> editor.putFloat(k, v)
                        is Boolean -> editor.putBoolean(k, v)
                        is Number -> editor.putLong(k, v.toLong())
                        is List<*> -> editor.putStringSet(k, v.filterIsInstance<String>().toSet())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing key $k in namespace $ns", e)
                }
            }
        }
        editor.apply()
    }
}
