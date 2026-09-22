package com.tamanna.enterprise.purchase

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class Purchase(
    val id: Long,
    val date: String,
    val productCode: String,
    val productName: String,
    val quantity: Int,
    val purchasePrice: Double,
    val supplier: String,
    val memoNumber: String
)

object PurchaseStorage {
    private const val PREFS = "tamanna_enterprise_purchases"
    private const val KEY_PURCHASES = "purchases"

    fun getPurchases(context: Context): List<Purchase> {
        val raw = BusinessStorage.prefs(context, PREFS)
            .getString(KEY_PURCHASES, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    Purchase(
                        id = item.getLong("id"),
                        date = item.getString("date"),
                        productCode = item.getString("productCode"),
                        productName = item.getString("productName"),
                        quantity = item.getInt("quantity"),
                        purchasePrice = item.getDouble("purchasePrice"),
                        supplier = item.getString("supplier"),
                        memoNumber = item.getString("memoNumber")
                    )
                )
            }
        }.sortedByDescending { it.id }
    }

    fun addPurchase(context: Context, purchase: Purchase): Boolean {
        val purchases = getPurchases(context).toMutableList()
        val usedIds = purchases.asSequence().map { it.id }.toHashSet()
        var uniqueId = purchase.id
        while (usedIds.contains(uniqueId)) {
            uniqueId++
        }
        val normalizedPurchase = purchase.copy(id = uniqueId)
        purchases.add(normalizedPurchase)
        savePurchases(context, purchases)

        return getPurchases(context).any {
            it.id == normalizedPurchase.id &&
                it.productCode.equals(normalizedPurchase.productCode, true) &&
                it.quantity == normalizedPurchase.quantity &&
                it.purchasePrice == normalizedPurchase.purchasePrice
        }
    }

    private fun savePurchases(context: Context, purchases: List<Purchase>) {
        val array = JSONArray()
        purchases.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("date", it.date)
                put("productCode", it.productCode)
                put("productName", it.productName)
                put("quantity", it.quantity)
                put("purchasePrice", it.purchasePrice)
                put("supplier", it.supplier)
                put("memoNumber", it.memoNumber)
            })
        }
        BusinessStorage.prefs(context, PREFS)
            .edit().putString(KEY_PURCHASES, array.toString()).apply()
    }
}
