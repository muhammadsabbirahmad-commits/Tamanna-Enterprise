package com.tamanna.enterprise.sales

import android.content.Context
import com.tamanna.enterprise.business.BusinessStorage
import org.json.JSONArray
import org.json.JSONObject

data class Sale(
    val id: Long,
    val transactionId: String,
    val date: String,
    val productCode: String,
    val productName: String,
    val quantity: Int,
    val salePrice: Double,
    val purchasePrice: Double,
    val customer: String
)

object SalesStorage {
    private const val PREFS = "tamanna_enterprise_sales"
    private const val KEY_SALES = "sales"

    fun getSales(context: Context): List<Sale> {
        val raw = BusinessStorage.prefs(context, PREFS)
            .getString(KEY_SALES, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(Sale(
                    item.getLong("id"),
                    item.optString("transactionId", item.getLong("id").toString()),
                    item.getString("date"),
                    item.getString("productCode"),
                    item.getString("productName"),
                    item.getInt("quantity"),
                    item.getDouble("salePrice"),
                    item.getDouble("purchasePrice"),
                    item.optString("customer", "")
                ))
            }
        }.sortedByDescending { it.id }
    }

    fun addSale(context: Context, sale: Sale): Boolean {
        val sales = getSales(context).toMutableList()
        val usedIds = sales.asSequence().map { it.id }.toHashSet()
        var uniqueId = sale.id
        while (usedIds.contains(uniqueId)) {
            uniqueId++
        }
        val normalizedSale = sale.copy(id = uniqueId)
        sales.add(normalizedSale)
        val array = JSONArray()
        sales.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("transactionId", it.transactionId)
                put("date", it.date)
                put("productCode", it.productCode)
                put("productName", it.productName)
                put("quantity", it.quantity)
                put("salePrice", it.salePrice)
                put("purchasePrice", it.purchasePrice)
                put("customer", it.customer)
            })
        }
        BusinessStorage.prefs(context, PREFS)
            .edit().putString(KEY_SALES, array.toString()).apply()

        return getSales(context).any {
            it.id == normalizedSale.id &&
                it.transactionId == normalizedSale.transactionId &&
                it.productCode.equals(normalizedSale.productCode, true) &&
                it.quantity == normalizedSale.quantity
        }
    }

    fun replaceTransactionSales(context: Context, transactionId: String, replacements: List<Sale>): Boolean {
        if (transactionId.isBlank() || replacements.isEmpty() ||
            replacements.any { it.transactionId != transactionId || it.quantity <= 0 || it.salePrice < 0.0 || it.purchasePrice < 0.0 }
        ) return false
        val current = getSales(context)
        val oldIds = current.filter { it.transactionId == transactionId }.map { it.id }.toSet()
        if (oldIds.isEmpty()) return false
        val kept = current.filterNot { it.transactionId == transactionId }
        val normalized = replacements.mapIndexed { index, sale ->
            val preferredId = sale.id
            val uniqueId = if (preferredId !in kept.map { it.id }.toSet() && replacements.count { it.id == preferredId } == 1) preferredId
                else (System.currentTimeMillis() + index)
            sale.copy(id = uniqueId)
        }
        saveSales(context, kept + normalized)
        return getSales(context).filter { it.transactionId == transactionId }.size == replacements.size
    }

    fun removeByTransaction(context: Context, transactionId: String) {
        saveSales(context, getSales(context).filterNot { it.transactionId == transactionId })
    }

    private fun saveSales(context: Context, sales: List<Sale>) {
        val array = JSONArray()
        sales.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("transactionId", it.transactionId)
                put("date", it.date)
                put("productCode", it.productCode)
                put("productName", it.productName)
                put("quantity", it.quantity)
                put("salePrice", it.salePrice)
                put("purchasePrice", it.purchasePrice)
                put("customer", it.customer)
            })
        }
        BusinessStorage.prefs(context, PREFS)
            .edit().putString(KEY_SALES, array.toString()).apply()
    }
}
