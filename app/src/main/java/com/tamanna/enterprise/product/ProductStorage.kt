package com.tamanna.enterprise.product

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Product(
    val code: String,
    val name: String,
    val purchasePrice: Double,
    val salePrice: Double,
    val stockQuantity: Int
)

object ProductStorage {
    private const val PREFS = "tamanna_enterprise_products"
    private const val KEY_PRODUCTS = "products"

    fun getProducts(context: Context): List<Product> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PRODUCTS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(Product(
                    item.getString("code"),
                    item.getString("name"),
                    item.getDouble("purchasePrice"),
                    item.getDouble("salePrice"),
                    item.getInt("stockQuantity")
                ))
            }
        }
    }

    fun addProduct(context: Context, product: Product): Boolean {
        val products = getProducts(context).toMutableList()
        if (products.any { it.code.equals(product.code, ignoreCase = true) }) return false
        products.add(product)
        saveProducts(context, products)
        return true
    }

    private fun saveProducts(context: Context, products: List<Product>) {
        val array = JSONArray()
        products.forEach {
            array.put(JSONObject().apply {
                put("code", it.code)
                put("name", it.name)
                put("purchasePrice", it.purchasePrice)
                put("salePrice", it.salePrice)
                put("stockQuantity", it.stockQuantity)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PRODUCTS, array.toString()).apply()
    }
}