package com.tamanna.enterprise.sales

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Sale(
    val id: Long,
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
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SALES, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(Sale(
                    item.getLong("id"),
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

    fun addSale(context: Context, sale: Sale) {
        val sales = getSales(context).toMutableList()
        sales.add(sale)
        val array = JSONArray()
        sales.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("date", it.date)
                put("productCode", it.productCode)
                put("productName", it.productName)
                put("quantity", it.quantity)
                put("salePrice", it.salePrice)
                put("purchasePrice", it.purchasePrice)
                put("customer", it.customer)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SALES, array.toString()).apply()
    }
}