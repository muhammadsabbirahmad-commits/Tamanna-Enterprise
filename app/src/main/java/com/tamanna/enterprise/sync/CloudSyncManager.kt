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

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"

    // সমস্ত প্রেফারেন্স ফাইল নেমস্পেস
    private val namespaces = listOf(
        "tamanna_enterprise_settings",
        "tamanna_business_prefs",
        "tamanna_inventory_prefs",
        "tamanna_customers_prefs",
        "tamanna_transactions_prefs",
        "tamanna_enterprise_products"
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * ইউজারের রোল অনুযায়ী সঠিক ক্লাউড ব্যাকআপ পাথ বের করে
     */
    fun getTargetSyncDoc(context: Context, onResult: (DocumentReference?) -> Unit) {
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) {
            onResult(null)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val localUser = SecurityStorage.getCurrentUser(context)
        val businessAccount = BusinessAccountStorage.get(context)
        val isPartner = localUser?.role == "PARTNER"

        // পার্টনার হলে ওনারের UID নিবে, ওনার হলে নিজের UID নিবে
        val ownerUid = if (isPartner) {
            businessAccount.ownerUid.ifBlank { businessAccount.businessId }
        } else {
            authUser.uid
        }

        if (ownerUid.isBlank()) {
            onResult(null)
            return
        }

        val ref = db.collection("users").document(ownerUid).collection("data").document("backup")
        onResult(ref)
    }

    /**
     * কোনো হ্যাং বা ল্যাগ ছাড়া দ্রুত এবং সরাসরি সিঙ্ক সম্পাদন করে
     */
    fun simpleSync(context: Context, onComplete: (String, Boolean) -> Unit) {
        val appContext = context.applicationContext

        // UI থ্রেডে রেসপন্স নিশ্চিত করার নিরাপদ ফাংশন
        fun returnResult(msg: String, success: Boolean) {
            mainHandler.post { onComplete(msg, success) }
        }

        getTargetSyncDoc(appContext) { metaRef ->
            if (metaRef == null) {
                returnResult("অ্যাকাউন্টের তথ্য পাওয়া যায়নি। অনুগ্রহ করে পুনরায় লগইন করুন।", false)
                return@getTargetSyncDoc
            }

            val localUser = SecurityStorage.getCurrentUser(appContext)
            val isPartner = localUser?.role == "PARTNER"

            if (isPartner) {
                // ==================== পার্টনার মোড (PULL ONLY) ====================
                metaRef.get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            try {
                                for (ns in namespaces) {
                                    val cloudData = doc.get(ns) as? Map<*, *>
                                    if (cloudData != null) {
                                        val prefs = BusinessStorage.prefs(appContext, ns)
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
                                returnResult("মালিকের ক্লাউড থেকে সফলভাবে সমস্ত আপডেট তথ্য নেওয়া হয়েছে! ✅", true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Parsing error during pull", e)
                                returnResult("ডেটা প্রসেস করতে সমস্যা হয়েছে।", false)
                            }
                        } else {
                            returnResult("ক্লাউডে মালিকের কোনো সংরক্ষিত ব্যাকআপ পাওয়া যায়নি।", false)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Firestore pull failed", e)
                        returnResult("ডাটা সিঙ্ক করতে ব্যর্থ হয়েছে। নেটওয়ার্ক চেক করুন।", false)
                    }
            } else {
                // ==================== ওনার মোড (PUSH ONLY) ====================
                try {
                    val dataMap = mutableMapOf<String, Any>()
                    for (ns in namespaces) {
                        val prefs = BusinessStorage.prefs(appContext, ns)
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
                    dataMap["updatedAt"] = System.currentTimeMillis()

                    metaRef.set(dataMap)
                        .addOnSuccessListener {
                            returnResult("সফলভাবে সমস্ত তথ্য ক্লাউডে সেভ হয়েছে! ☁️", true)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Firestore push failed", e)
                            returnResult("ক্লাউডে ব্যাকআপ পাঠাতে সমস্যা হয়েছে।", false)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Processing error during push", e)
                    returnResult("ডাটা প্রস্তুত করতে সমস্যা হয়েছে।", false)
                }
            }
        }
    }
}
